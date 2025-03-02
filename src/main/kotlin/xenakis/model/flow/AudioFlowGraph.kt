package xenakis.model.flow

import reaktive.value.now
import xenakis.model.Logger
import xenakis.model.obj.BusObject
import xenakis.model.registry.ObjectRegistry
import xenakis.model.score.ObjectPosition
import xenakis.model.score.SynthObject
import java.util.*

class AudioFlowGraph(private val flows: AudioFlows, private val nodeTree: NodeTree) :
    ObjectRegistry.Listener<BusObject> {
    private val activeSynthObjects = mutableMapOf<SynthObject, MutableSet<ServerNode.ActiveSynth>>()
    private val suffixes = mutableMapOf<Pair<SynthObject, ObjectPosition>, Int>()
    private val flowGroups = mutableMapOf<BusObject, ServerNode.FlowGroup>()
    private val order = LinkedList<ServerNode>()
    private val readFrom = mutableMapOf<BusObject, MutableSet<ServerNode>>()
    private val writeTo = mutableMapOf<BusObject, MutableSet<ServerNode>>()
    private val graph = ReachabilityGraph<ServerNode>()

    override fun added(obj: BusObject, idx: Int) {
        val node = ServerNode.FlowGroup(obj, flows)
        graph.addVertex(node)
        addFlowToNode(node, node)
        reorderNodeTree()
    }

    override fun removed(obj: BusObject, idx: Int) {
        val node = flowGroups.remove(obj) ?: error("No active flows for bus $obj")
        removeNode(node)
        graph.removeVertex(node)
        nodeTree.free(node)
        reorderNodeTree()
    }

    fun activateFlow(flow: AudioFlow) {
        val bus = flow.associatedBus
        val node = flowGroups.getValue(bus)
        addFlowToNode(node, flow)
        for (f in node.flows()) {
            if (f.index >= flow.index) {
                nodeTree.rename("~flows_${bus.name.now}_${f.index - 1}", "~flows_${bus.name.now}_${f.index}")
            }
        }
        reorderNodeTree()
    }

    fun deactivateFlow(flow: AudioFlow) {
        val node = flowGroups.getValue(flow.associatedBus)
        removeFlowFromNode(node, flow)
        reorderNodeTree()
    }

    fun movedFlow(flow: AudioFlow) {

    }

    private fun reorderNodeTree() {
        BubbleSort.sort(order, Comparator(::comparisonFunction), nodeTree::moveAfter)
    }

    private fun comparisonFunction(v: ServerNode, u: ServerNode): Int = when {
        graph.reachable(u, v) -> +1
        graph.reachable(v, u) -> -1
        v is ServerNode.ActiveSynth && u is ServerNode.ActiveSynth -> v.absolutePosition.y.compareTo(u.absolutePosition.y)
        v is ServerNode.ActiveSynth && u is ServerNode.FlowGroup -> -1
        v is ServerNode.FlowGroup && u is ServerNode.ActiveSynth -> +1
        else -> 0
    }

    private fun addFlowToNode(node: ServerNode, flow: Flow) {
        for (input in flow.getConnectedBusses(FlowType.In)) {
            readFrom.getOrPut(input, ::mutableSetOf).add(node)
            for (writer in writeTo[input].orEmpty()) {
                graph.addEdge(writer, node)
            }
        }
        for (output in flow.getConnectedBusses(FlowType.Out)) {
            writeTo.getOrPut(output, ::mutableSetOf).add(node)
            for (reader in readFrom[output].orEmpty()) {
                graph.addEdge(node, reader)
            }
        }
    }

    fun insert(obj: SynthObject, absolutePosition: ObjectPosition): SynthOrder {
        val instances = activeSynthObjects.getOrPut(obj, ::mutableSetOf)
        val takenSuffixes = instances.mapTo(mutableSetOf()) { o -> o.suffix }
        val suffix = (0..Int.MAX_VALUE).first { n -> n !in takenSuffixes }
        val node = ServerNode.ActiveSynth(obj, absolutePosition, suffix)
        suffixes[obj to absolutePosition] = suffix
        instances.add(node)
        addNode(node)
        reorderNodeTree()
        Logger.fine("marked start for $node, suffix = $suffix", Logger.Category.Playback)
        return when (val index = order.indexOf(node)) {
            0 -> SynthOrder("'addToHead'", "s.defaultGroup")
            else -> SynthOrder("'addAfter'", "~${order[index - 1].superColliderName}")
        }
    }

    fun remove(obj: SynthObject, absolutePosition: ObjectPosition) {
        val suffix = suffixes.remove(obj to absolutePosition)
        if (suffix == null) {
            Logger.warn("could not remove $obj at $absolutePosition", Logger.Category.Playback)
        } else {
            val node = ServerNode.ActiveSynth(obj, absolutePosition, suffix)
            activeSynthObjects.getValue(obj).remove(node)
            removeNode(node)
            Logger.fine("marked end for $obj, suffix = $suffix", Logger.Category.Playback)
        }
        reorderNodeTree()
    }

    private fun addNode(node: ServerNode) {
        addFlowToNode(node, node)
    }

    private fun addEdge(edge: Edge) {
        val indexFrom = order.indexOf(edge.from)
        val indexTo = order.indexOf(edge.to)
        if (indexFrom == -1 || indexTo == -1) error("A node of $edge is not in the topological sorting")
        if (indexTo > indexFrom) return
        order.removeAt(indexTo)
        order.add(indexFrom + 1, edge.to)
        nodeTree.moveAfter(edge.to, target = edge.from)
        for (output in edge.to.getConnectedBusses(FlowType.In, FlowType.InOut)) {
            for (reader in readFrom[output].orEmpty()) {
                addEdge(Edge(edge.to, reader, output))
            }
        }
    }

    private fun removeNode(node: ServerNode) {
        order.remove(node)
        removeFlowFromNode(node, node)
    }

    private fun removeFlowFromNode(node: ServerNode, flow: Flow) {
        for (input in flow.getConnectedBusses(FlowType.In)) {
            check(readFrom.getValue(input).remove(node)) { "Could not remove $node from readers from $input" }
            graph.removeEd
        }
        for (output in flow.getConnectedBusses(FlowType.InOut, FlowType.Out)) {
            check(writeTo.getValue(output).remove(node)) { "Could not remove $node from writers to $output" }
        }
    }

    private fun <S : ServerNode> topologicalSort(
        activeSynths: Collection<S>,
        stopWhen: (List<S>) -> Boolean = { false }
    ): List<S> {
        val dependencies = mutableMapOf<BusObject, MutableSet<S>>()
        val transformers = mutableMapOf<BusObject, MutableSet<S>>()
        val dependents = mutableMapOf<BusObject, MutableSet<S>>()
        for (s in activeSynths) {
            for (bus in s.getConnectedBusses(FlowType.Out)) {
                dependencies.getOrPut(bus, ::mutableSetOf).add(s)
            }
            for (bus in s.getConnectedBusses(FlowType.InOut)) {
                transformers.getOrPut(bus, ::mutableSetOf).add(s)
                dependents.getOrPut(bus, ::mutableSetOf).add(s)
            }
            for (bus in s.getConnectedBusses(FlowType.In)) {
                dependents.getOrPut(bus, ::mutableSetOf).add(s)
            }
        }
        val q: Queue<S> = PriorityQueue(activeSynths)
        val enqueued = activeSynths.toMutableSet()
        val order = mutableListOf<S>()
        val visited = mutableSetOf<S>()
        while (q.isNotEmpty() && order.size < activeSynths.size) {
            val s = q.poll()
            enqueued.remove(s)
            if (s in visited) continue
            val transformed = s.getConnectedBusses(FlowType.InOut)
            val inputs = s.getConnectedBusses(FlowType.In)
            if (
                inputs.all { bus -> dependencies[bus].isNullOrEmpty() && transformers[bus].isNullOrEmpty() } &&
                transformed.all { bus -> dependencies[bus].isNullOrEmpty() }
            ) {
                if (visited.add(s)) {
                    order.add(s)
                    if (stopWhen(order)) return order
                }
                val enqueue = mutableSetOf<S>()
                for (bus in transformed) {
                    transformers[bus]!!.remove(s)
                    if (transformers[bus]!!.isEmpty()) {
                        enqueue.addAll(dependents[bus].orEmpty())
                    }
                }
                val outputs = s.getConnectedBusses(FlowType.Out)
                for (bus in outputs) {
                    dependencies[bus]!!.remove(s)
                    if (dependencies[bus]!!.isEmpty()) {
                        enqueue.addAll(dependents[bus].orEmpty())
                    }
                }
                q.addAll(enqueue - enqueued)
                enqueued.addAll(enqueue)
            }
        }
        return order
    }

    private data class Edge(val from: ServerNode, val to: ServerNode, val label: BusObject)
}