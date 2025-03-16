package xenakis.model.flow

import bundles.PublicProperty
import bundles.publicProperty
import kollektion.Counter
import reaktive.Observer
import reaktive.value.now
import xenakis.impl.BubbleSort
import xenakis.impl.ReachabilityGraph
import xenakis.model.Logger
import xenakis.model.flow.NodePlacement.AddAction
import xenakis.model.obj.BusObject
import xenakis.model.obj.GroupObject
import xenakis.model.registry.BusRegistry
import xenakis.model.registry.GroupRegistry
import xenakis.model.registry.ObjectRegistry
import xenakis.model.score.ObjectPosition
import xenakis.model.score.ScoreObject
import xenakis.model.score.SynthObject
import xenakis.sc.client.SuperColliderClient
import java.util.*

class AudioFlowGraph(
    private val flows: AudioFlows,
    private val nodeTree: NodeTree
) : ObjectRegistry.Listener<BusObject>, AudioFlows.Listener {
    private val activeSynthObjects = mutableMapOf<SynthObject, MutableSet<ActiveSynth>>()
    private val takenSuffixes = mutableMapOf<String, MutableSet<Int>>()
    private val suffixes = mutableMapOf<Pair<ScoreObject, ObjectPosition>, Int>()
    private val flowGroups = mutableMapOf<BusObject, FlowGroup>()
    private val order = LinkedList<AudioNode>()
    private val readFrom = mutableMapOf<BusObject, Counter<AudioNode>>()
    private val writeTo = mutableMapOf<BusObject, Counter<AudioNode>>()
    private val graph = ReachabilityGraph<AudioNode>()
    private val placeholderContents = mutableMapOf<GroupObject, MutableList<ActiveSynth>>()
    private val serverClearObserver: Observer

    init {
        flows.context[BusRegistry].addListener(this)
        flows.addListener(this)
        serverClearObserver = flows.context[SuperColliderClient].treeCleared.observe { _ ->
            rebuildFlowGraph()
        }
    }

    private fun rebuildFlowGraph() {
        reorderNodeTree()
        for (o in order) {
            nodeTree.addNode(o, NodePlacement(AddAction.AddToTail, "s.defaultGroup"))
            if (o is FlowGroup) {
                for (flow in o.flows()) {
                    nodeTree.addNode(flow, NodePlacement(AddAction.AddToTail, o.superColliderName.now))
                }
            }
        }
    }

    override fun added(obj: BusObject, idx: Int) {
        val node = FlowGroup(obj, flows)
        flowGroups[obj] = node
        addNode(node)
        reorderNodeTree()
        val placement = getPlacementFor(node)
        nodeTree.addNode(node, placement)
    }

    override fun removed(obj: BusObject, idx: Int) {
        val node = flowGroups.remove(obj) ?: error("No active flows for bus $obj")
        removeNode(node)
        nodeTree.removeNode(node)
        reorderNodeTree()
    }

    override fun activatedFlow(flow: AudioFlow) {
        val node = flowGroup(flow.associatedBus)
        addChildNode(node, flow)
        reorderNodeTree()
        val placement = getPlacementFor(flow)
        nodeTree.addNode(flow, placement)
    }

    override fun deactivatedFlow(flow: AudioFlow) {
        val node = flowGroup(flow.associatedBus)
        removeChildNode(node, flow)
        reorderNodeTree()
        nodeTree.removeNode(flow)
    }

    override fun movedFlow(flow: AudioFlow, oldIndex: Int, newIndex: Int) {
        if (flow.isActive.now) {
            val group = flowGroup(flow.associatedBus)
            if (newIndex == 0) nodeTree.moveToHead(group, flow)
            else nodeTree.moveAfter(group.flows()[newIndex - 1], flow)
        }
    }

    private fun getPlacementInOrder(node: AudioNode): NodePlacement {
        val idx = order.indexOf(node)
        return if (idx == 0) NodePlacement(AddAction.AddToHead, "s.defaultGroup")
        else NodePlacement(AddAction.AddAfter, order[idx - 1].superColliderName.now)
    }

    private fun getPlacementFor(node: AudioNode): NodePlacement = when (node) {
        is AudioFlow -> {
            val group = flowGroup(node.associatedBus)
            val flows = group.flows()
            val i = flows.indexOf(node)
            if (i == 0) NodePlacement(AddAction.AddToHead, group.superColliderName.now)
            else NodePlacement(AddAction.AddAfter, flows[i - 1].superColliderName.now)
        }

        is ActiveSynth -> {
            val group = node.obj.group.now.get() ?: node.obj.context[GroupRegistry].getDefault()!!
            if (group.isDefault) {
                getPlacementInOrder(node)
            } else {
                val synths = placeholderContents.getValue(group)
                BubbleSort.sort(synths, compareBy { s -> s.absolutePosition.y })
                var i = synths.binarySearchBy(node.absolutePosition.y) { s -> s.absolutePosition.y }
                if (i < 0) i = -(i + 1)
                synths.add(i, node)
                if (i == 0) NodePlacement(AddAction.AddToHead, group.superColliderName)
                else NodePlacement(AddAction.AddAfter, synths[i - 1].superColliderName.now)
            }
        }

        is FlowGroup -> getPlacementInOrder(node)
    }

    private fun reorderNodeTree() {
        BubbleSort.sort(order, Comparator(::comparisonFunction), nodeTree::moveAfter)
    }

    private fun comparisonFunction(v: AudioNode, u: AudioNode): Int = when {
        graph.reachable(u, v) -> +1
        graph.reachable(v, u) -> -1
        v is ActiveSynth && u is ActiveSynth -> v.absolutePosition.y.compareTo(u.absolutePosition.y)
        v is ActiveSynth && u is FlowGroup -> -1
        v is FlowGroup && u is ActiveSynth -> +1
        else -> 0
    }

    private fun addChildNode(node: AudioNode, child: AudioNode) {
        for (input in child.getInputs()) {
            readFrom.getOrPut(input, ::Counter).add(node)
            for (writer in writeTo[input]?.asSet().orEmpty()) {
                graph.addEdge(writer, node)
            }
        }
        for (output in child.getOutputs()) {
            writeTo.getOrPut(output, ::Counter).add(node)
            for (reader in readFrom[output]?.asSet().orEmpty()) {
                graph.addEdge(node, reader)
            }
        }
    }

    fun insert(obj: ScoreObject, absolutePosition: ObjectPosition): ScoreObjectInfo {
        val takenSuffixes = takenSuffixes[obj.name.now].orEmpty()
        val suffix = (0..Int.MAX_VALUE).first { n -> n !in takenSuffixes }
        suffixes[obj to absolutePosition] = suffix
        val name = if (suffix == 0) obj.name.now else "${obj.name.now}_$suffix"
        lateinit var group: GroupObject
        val defaultGroup = obj.context[GroupRegistry].getDefault()
        Logger.fine("mark start for $obj at $absolutePosition, suffix = $suffix", Logger.Category.Playback)
        if (obj is SynthObject) {
            val node = ActiveSynth(obj, absolutePosition, suffix)
            activeSynthObjects.getOrPut(obj, ::mutableSetOf).add(node)
            group = node.obj.groupObj
            if (group == defaultGroup) {
                addNode(node)
                reorderNodeTree()
            }
            val placement = getPlacementFor(node)
            val synthVar = "~synths['$name']"
            val info = ScoreObjectInfo(absolutePosition, name, synthVar, placement)
            return info
        } else return ScoreObjectInfo(absolutePosition, name, "<no name>", null)
    }

    fun remove(obj: ScoreObject, absolutePosition: ObjectPosition) {
        val suffix = suffixes.remove(obj to absolutePosition)
        if (suffix == null) {
            Logger.warn("could not remove $obj at $absolutePosition", Logger.Category.Playback)
        } else {
            if (obj is SynthObject) {
                val node = ActiveSynth(obj, absolutePosition, suffix)
                activeSynthObjects.getValue(obj).remove(node)
                removeNode(node)
                reorderNodeTree()
            }
            Logger.fine("marked end for $obj, suffix = $suffix", Logger.Category.Playback)
        }
    }

    private fun addNode(node: AudioNode) {
        graph.addVertex(node)
        order.add(node)
        addChildNode(node, node)
    }

    private fun removeNode(node: AudioNode) {
        graph.removeVertex(node)
        order.remove(node)
        removeChildNode(node, node)
    }

    private fun removeChildNode(node: AudioNode, child: AudioNode) {
        for (input in child.getInputs()) {
            if (!readFrom.getValue(input).remove(node)) {
                Logger.warn("Could not remove $node from readers from $input", Logger.Category.AudioFlow)
            }
            for (writer in writeTo[input]?.asSet().orEmpty()) {
                graph.removeEdge(writer, node)
            }
        }
        for (output in child.getOutputs()) {
            if (!writeTo.getValue(output).remove(node)) {
                Logger.warn("Could not remove $node from readers from $output", Logger.Category.AudioFlow)
            }
            for (reader in readFrom[output]?.asSet().orEmpty()) {
                graph.removeEdge(node, reader)
            }
        }
    }

    fun activeInstances(obj: ScoreObject): Set<ActiveSynth> =
        if (obj is SynthObject) activeSynthObjects[obj].orEmpty() else emptySet()

    fun clear() {
        for (activeSynth in activeSynthObjects.values.flatten()) {
            removeNode(activeSynth)
        }
        activeSynthObjects.clear()
    }

    fun flowGroup(bus: BusObject): FlowGroup = flowGroups.getValue(bus)

    fun getOrder(): List<AudioNode> = order

    companion object : PublicProperty<AudioFlowGraph> by publicProperty("AudioFlowGraph")
}