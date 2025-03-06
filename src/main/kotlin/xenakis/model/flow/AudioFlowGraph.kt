package xenakis.model.flow

import bundles.PublicProperty
import bundles.publicProperty
import kollektion.Counter
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
import java.util.*

class AudioFlowGraph(
    private val flows: AudioFlows,
    private val nodeTree: NodeTree
) : ObjectRegistry.Listener<BusObject>, AudioFlows.Listener {
    private val activeSynthObjects = mutableMapOf<SynthObject, MutableSet<ServerNode.ActiveSynth>>()
    private val takenSuffixes = mutableMapOf<String, MutableSet<Int>>()
    private val suffixes = mutableMapOf<Pair<ScoreObject, ObjectPosition>, Int>()
    private val flowGroups = mutableMapOf<BusObject, ServerNode.FlowGroup>()
    private val order = LinkedList<ServerNode>()
    private val readFrom = mutableMapOf<BusObject, Counter<ServerNode>>()
    private val writeTo = mutableMapOf<BusObject, Counter<ServerNode>>()
    private val graph = ReachabilityGraph<ServerNode>()
    private val placeholderContents = mutableMapOf<GroupObject, MutableList<ServerNode.ActiveSynth>>()

    init {
        flows.context[BusRegistry].addListener(this)
        flows.addListener(this)
    }

    override fun added(obj: BusObject, idx: Int) {
        val node = ServerNode.FlowGroup(obj, flows)
        flowGroups[obj] = node
        addNode(node)
        reorderNodeTree()
    }

    override fun removed(obj: BusObject, idx: Int) {
        val node = flowGroups.remove(obj) ?: error("No active flows for bus $obj")
        removeNode(node)
        nodeTree.free(node)
        reorderNodeTree()
    }

    override fun activatedFlow(flow: AudioFlow) {
        val bus = flow.associatedBus
        val node = flowGroups.getValue(bus)
        addFlowToNode(node, flow)
        reorderNodeTree()
        val placement = getPlacementFor(flow)
        nodeTree.addFlow(flow, placement)
    }

    private fun getPlacementFor(flow: AudioFlow): NodePlacement {
        val group = flowGroup(flow.associatedBus)
        val flows = group.flows()
        val i = flows.indexOf(flow)
        return if (i == 0) NodePlacement(AddAction.AddToHead, group.superColliderName)
        else NodePlacement(AddAction.AddAfter, flows[i - 1].superColliderName.now)
    }

    override fun deactivatedFlow(flow: AudioFlow) {
        val node = flowGroups.getValue(flow.associatedBus)
        removeFlowFromNode(node, flow)
        reorderNodeTree()
        nodeTree.removeFlow(flow)
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
            readFrom.getOrPut(input, ::Counter).add(node)
            for (writer in writeTo[input]?.asSet().orEmpty()) {
                graph.addEdge(writer, node)
            }
        }
        for (output in flow.getConnectedBusses(FlowType.Out)) {
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
        val name = if (suffix == 0) "~${obj.name.now}" else "~${obj.name.now}_$suffix"
        var node: ServerNode.ActiveSynth? = null
        lateinit var group: GroupObject
        val defaultGroup = obj.context[GroupRegistry].getDefaultGroup()
        if (obj is SynthObject) {
            node = ServerNode.ActiveSynth(obj, absolutePosition, suffix)
            activeSynthObjects.getOrPut(obj, ::mutableSetOf).add(node)
            group = node.obj.group.now.get<GroupObject>()
            if (group == defaultGroup) {
                addNode(node)
                reorderNodeTree()
            }
        }
        Logger.fine("marked start for $obj at $absolutePosition, suffix = $suffix", Logger.Category.Playback)
        return when {
            node == null -> ScoreObjectInfo(absolutePosition, name, null)
            group != defaultGroup -> {
                val objects = placeholderContents.getOrPut(group, ::mutableListOf)
                var i = objects.binarySearchBy(absolutePosition.y) { s -> s.absolutePosition.y }
                if (i < 0) i = -(i + 1)
                objects.add(i, node)
                ScoreObjectInfo(absolutePosition, name, NodePlacement(AddAction.AddToHead, group.superColliderName))
            }

            else -> {
                val placement = when (val index = order.indexOf(node)) {
                    0 -> NodePlacement(AddAction.AddToHead, "s.defaultGroup")
                    else -> NodePlacement(AddAction.AddAfter, "~${order[index - 1].superColliderName}")
                }
                ScoreObjectInfo(absolutePosition, name, placement)
            }
        }
    }

    fun remove(obj: ScoreObject, absolutePosition: ObjectPosition) {
        val suffix = suffixes.remove(obj to absolutePosition)
        if (suffix == null) {
            Logger.warn("could not remove $obj at $absolutePosition", Logger.Category.Playback)
        } else {
            if (obj is SynthObject) {
                val node = ServerNode.ActiveSynth(obj, absolutePosition, suffix)
                activeSynthObjects.getValue(obj).remove(node)
                removeNode(node)
                reorderNodeTree()
            }
            Logger.fine("marked end for $obj, suffix = $suffix", Logger.Category.Playback)
        }
    }

    private fun addNode(node: ServerNode) {
        graph.addVertex(node)
        order.add(node)
        addFlowToNode(node, node)
    }

    private fun removeNode(node: ServerNode) {
        graph.removeVertex(node)
        order.remove(node)
        removeFlowFromNode(node, node)
    }

    private fun removeFlowFromNode(node: ServerNode, flow: Flow) {
        for (input in flow.getConnectedBusses(FlowType.In)) {
            if (!readFrom.getValue(input).remove(node)) {
                Logger.warn("Could not remove $node from readers from $input", Logger.Category.AudioFlow)
            }
            for (writer in writeTo[input]?.asSet().orEmpty()) {
                graph.removeEdge(writer, node)
            }
        }
        for (output in flow.getConnectedBusses(FlowType.InOut, FlowType.Out)) {
            if (!writeTo.getValue(output).remove(node)) {
                Logger.warn("Could not remove $node from readers from $output", Logger.Category.AudioFlow)
            }
            for (reader in readFrom[output]?.asSet().orEmpty()) {
                graph.removeEdge(node, reader)
            }
        }
    }

    fun activeInstances(obj: ScoreObject): Set<ServerNode.ActiveSynth> =
        if (obj is SynthObject) activeSynthObjects[obj].orEmpty() else emptySet()

    fun clear() {
        for (activeSynth in activeSynthObjects.values.flatten()) {
            removeNode(activeSynth)
        }
        activeSynthObjects.clear()
    }

    fun flowGroup(bus: BusObject): ServerNode.FlowGroup = flowGroups.getValue(bus)

    fun getOrder(): List<ServerNode> = order

    companion object : PublicProperty<AudioFlowGraph> by publicProperty("AudioFlowGraph")
}