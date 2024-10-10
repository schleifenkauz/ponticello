package xenakis.model

import hextant.context.Context
import hextant.context.withoutUndo
import hextant.core.editor.ListenerManager
import hextant.serial.EditorRoot
import hextant.undo.AbstractEdit
import hextant.undo.UndoManager
import javafx.geometry.Point2D
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import reaktive.Observer
import reaktive.value.now
import reaktive.value.reactiveVariable
import xenakis.impl.Point2DSerializer
import xenakis.impl.ScWriter
import xenakis.impl.SuperColliderClient
import xenakis.impl.copy
import xenakis.sc.editor.*
import xenakis.ui.plus
import java.util.*

@Serializable
class AudioFlowGraph(
    private val _nodes: MutableList<BusNode>,
    private val _flows: MutableList<AudioFlow>,
) : ObjectRegistry.Listener<BusObject>, XenakisProject.ProjectComponent {
    @Transient
    private lateinit var registry: BusRegistry

    val context get() = registry.context

    @Transient
    private lateinit var client: SuperColliderClient

    @Transient
    private lateinit var undoManager: UndoManager

    @Transient
    val views = ListenerManager.createWeakListenerManager<View>()

    @Transient
    private val nodeNameObservers = mutableMapOf<BusObject, Observer>()

    override val componentName: String
        get() = "flow_graph"

    val flows: List<AudioFlow> get() = _flows
    val nodes: List<BusNode> get() = _nodes

    fun initialize(context: Context) {
        registry = context[BusRegistry]
        client = context[SuperColliderClient]
        registry.addListener(this)
        undoManager = context[UndoManager]
        for (obj in nodes) obj.ref.resolve(context[BusRegistry])
        for (flow in flows) {
            flow.source.resolve(context[BusRegistry])
            flow.target.resolve(context[BusRegistry])
        }

        client.run { defineAudioFlow() }
    }

    private fun edges(node: ObjectReference) = flows.filter { f -> f.source == node }

    fun getNode(bus: ObjectReference) = nodes.find { it.ref == bus }
        ?: throw NoSuchElementException("No node for bus ${bus.get<BusObject>().name.now} found")

    @Transient
    var order = findFlowOrder() ?: error("audio flow graph contains cycles")
        private set

    fun addFlow(source: BusNode, target: BusNode): AudioFlow? {
        val synth = AdhocSynthEditor(context)
        val flow = AudioFlow(source.ref, target.ref, EditorRoot.create(synth))
        context.withoutUndo {
            synth.name.setText(flow.synthName.removePrefix("~"))
            synth.block.variables.addLast(IdentifierEditor(context, "snd"))
            synth.block.statements.addLast(assign("snd", `in`(context, source.bus)))
            synth.block.statements.addLast(out(context, target.bus, ScExprExpander(context, "snd")))
        }
        return if (addFlow(flow)) flow
        else null
    }

    fun addFlow(flow: AudioFlow): Boolean {
        if (_flows.any { f -> f.source == flow.source && f.target == flow.target }) return false
        _flows.add(flow)
        val newOrder = findFlowOrder()
        if (newOrder == null) {
            _flows.remove(flow)
            return false
        }
        order = newOrder
        client.run { redefineAudioFlow(this) }
        undoManager.record(Edit.AddFlow(this, flow))
        views.notifyListeners { addedFlow(flow) }
        return true
    }

    fun removeFlow(flow: AudioFlow) {
        _flows.remove(flow)
        order = findFlowOrder()!!
        client.run {
            +"${flow.synthName}.free"
            +"${flow.synthName} = nil"
            redefineAudioFlow(this)
        }
        undoManager.record(Edit.RemoveFlow(this, flow))
        views.notifyListeners { removedFlow(flow) }
    }

    fun split(node: BusNode, output: Boolean) {
        val name = node.bus.name.now
        val channels = node.bus.channels.now
        if (channels <= 1) return
        for (idx in 1..channels) {
            val subBusName = "$name$idx"
            val subNode = nodes.find { it.ref.getName() == subBusName } ?: run {
                val subBus = if (registry.has(subBusName)) registry.get(subBusName) else
                    BusObject(reactiveVariable(subBusName), node.bus.rate.copy(), reactiveVariable(1))
                        .also { newBus -> registry.add(newBus) }
                val deltaX = (idx - (channels / 2 + 1)) * 100.0
                BusNode(subBus.createReference(), node.position + Point2D(deltaX, 100.0)).also { n -> addNode(n) }
            }
            if (associatedFlows(subNode).any { f -> f.source == node.ref || f.target == node.ref }) {
                return
            }
            val flow = if (!output) {
                val synth = AdhocSynthEditor(registry.context)
                context.withoutUndo {
                    synth.name.setText("flow_${name}_${subBusName}")
                    synth.block.variables.addLast(IdentifierEditor(context, "snd"))
                    synth.block.statements.addLast(
                        assign("snd", `in`(context, selectSubBus(context, node.ref, idx), node.bus.rate.now, 1))
                    )
                    synth.block.statements.addLast(
                        out(context, subNode.bus, ScExprExpander(context, "snd"))
                    )
                }
                AudioFlow(node.ref, subNode.ref, EditorRoot.create(synth))
            } else {
                val synth = AdhocSynthEditor(registry.context)
                context.withoutUndo {
                    synth.name.setText("flow_${subBusName}_${name}")
                    synth.block.variables.addLast(IdentifierEditor(context, "snd"))
                    synth.block.statements.addLast(assign("snd", `in`(context, subNode.bus)))
                    synth.block.statements.addLast(
                        out(
                            context,
                            selectSubBus(context, node.ref, idx),
                            ScExprExpander(context, "snd"),
                            node.bus.rate.now
                        )
                    )
                }
                AudioFlow(subNode.ref, node.ref, EditorRoot.create(synth))
            }
            addFlow(flow)
        }
    }

    fun updateFlow() {
        client.run { redefineAudioFlow(this) }
    }

    fun redefineAudioFlow(writer: ScWriter) {
        writer.clearAudioFlow()
        writer.defineAudioFlow()
    }

    private fun ScWriter.clearAudioFlow() {
        +"ServerTree.remove(~setup_flow)"
        for (flow in flows) {
            +"${flow.synthName}.free"
            +"${flow.synthName} = nil"
        }
    }

    private fun ScWriter.defineAudioFlow() {
        appendBlock("~setup_flow = ") {
            setupAudioFlow()
            +"'setup audio flow'.postln"
        }
        +"ServerTree.add(~setup_flow)"
        +"if (s.serverRunning) { ~setup_flow.value }"

    }

    private fun ScWriter.setupAudioFlow() {
        for ((group, flows) in order) {
            var prev = group.superColliderName
            for (flow in flows) {
                val synth = flow.synth.editor.result.now
                val addAction = if (prev.startsWith("~grp")) "'addToHead'" else "'addAfter'"
                synth.writeCode(writer, registry.context, flow.synthName, prev, addAction, wrapInTask = true)
                prev = flow.synthName
            }
        }
    }

    override fun added(obj: BusObject, idx: Int) {}

    override fun removed(obj: BusObject, idx: Int) {
        for (node in nodes.toList()) {
            if (node.ref.get<BusObject>() == obj) {
                removeNode(node)
            }
        }
    }

    fun addNode(bus: ObjectReference, position: Point2D): Boolean {
        val obj = BusNode(bus, position)
        return addNode(obj)
    }

    private fun addNode(node: BusNode): Boolean {
        if (nodes.any { n -> n.ref == node.ref }) {
            return false
        }
        _nodes.add(node)
        val obj = node.ref.get<BusObject>()
        nodeNameObservers[obj] = obj.name.observe { _, oldName, _ -> renamedNode(node, oldName) }
        undoManager.record(Edit.AddNode(this, node))
        views.notifyListeners { addedNode(node) }
        return true
    }

    private fun renamedNode(obj: BusNode, oldName: String) {
        client.run {
            for (flow in associatedFlows(obj)) {
                val newSourceName = flow.source.get<BusObject>().name.now
                val newTargetName = flow.target.get<BusObject>().name.now
                val oldFlowName =
                    if (flow.target == obj.ref) "~flow_${newSourceName}_${oldName}"
                    else "~flow_${oldName}_$newTargetName"
                val newFlowName = flow.synthName
                +"$newFlowName = $oldFlowName"
                +"$oldFlowName = nil"
            }
        }
    }

    fun move(node: BusNode, position: Point2D) {
        undoManager.record(Edit.MoveNode(this, node, node.position, position))
        node.position = position
        views.notifyListeners { movedNode(node) }
    }

    fun removeNode(node: BusNode) {
        if (!_nodes.remove(node)) return
        val associatedFlows = associatedFlows(node)
        views.notifyListeners { removedNode(node) }
        for (flow in associatedFlows) removeFlow(flow)
        nodeNameObservers.remove(node.ref.get())?.kill()
        undoManager.record(Edit.RemoveNode(this, node, associatedFlows))
    }

    fun associatedFlows(bus: BusNode) =
        flows.filter { f -> f.source == bus.ref || f.target == bus.ref }

    private fun findFlowOrder(): Map<GroupObject, List<AudioFlow>>? {
        val orders = mutableMapOf<GroupObject, List<AudioFlow>>()
        for ((group, flows) in flows.groupBy { f -> getGroup(f) }) {
            val q: Queue<AudioFlow> = LinkedList()
            val order = mutableListOf<AudioFlow>()
            val dependencies = mutableMapOf<ObjectReference, Int>()
            for (flow in flows) dependencies[flow.target] = (dependencies[flow.target] ?: 0) + 1
            for (flow in flows) if ((dependencies[flow.source] ?: 0) == 0) q.offer(flow)
            while (q.isNotEmpty()) {
                val flow = q.poll()
                order.add(flow)
                dependencies[flow.target] = dependencies[flow.target]!! - 1
                if (dependencies[flow.target] == 0) {
                    for (e in edges(flow.target)) {
                        if (getGroup(e) == group) q.offer(e)
                    }
                }
            }
            if (order.size == flows.size) orders.put(group, order) else return null
        }
        return orders
    }

    private fun getGroup(f: AudioFlow) =
        f.synth.editor.result.now.group.reference?.get<GroupObject>() ?: context[GroupRegistry].getDefault()

    @Serializable
    data class BusNode(val ref: ObjectReference, @Serializable(with = Point2DSerializer::class) var position: Point2D) {
        val bus get() = ref.get<BusObject>()
    }

    @Serializable
    class AudioFlow(
        val source: ObjectReference,
        val target: ObjectReference,
        val synth: EditorRoot<AdhocSynthEditor>
    ) {
        val synthName get() = "~flow_${source.get<BusObject>().name.now}_${target.get<BusObject>().name.now}"

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as AudioFlow

            if (source != other.source) return false
            if (target != other.target) return false

            return true
        }

        override fun hashCode(): Int {
            var result = source.hashCode()
            result = 31 * result + target.hashCode()
            return result
        }
    }

    companion object {
        fun createDefault(): AudioFlowGraph = AudioFlowGraph(mutableListOf(), mutableListOf())
    }

    private sealed class Edit(protected val graph: AudioFlowGraph) : AbstractEdit() {
        class RemoveNode(
            graph: AudioFlowGraph,
            private val node: BusNode,
            private val associatedFlows: List<AudioFlow>
        ) : Edit(graph) {
            override val actionDescription: String
                get() = "Remove audio flow graph node"

            override fun doUndo() {
                graph.addNode(node)
                for (flow in associatedFlows) graph.addFlow(flow)
            }

            override fun doRedo() {
                graph.removeNode(node)
            }
        }

        class AddNode(graph: AudioFlowGraph, private val node: BusNode) : Edit(graph) {
            override val actionDescription: String
                get() = "Add audio flow graph node"

            override fun doUndo() {
                graph.removeNode(node)
            }

            override fun doRedo() {
                graph.addNode(node)
            }
        }

        class AddFlow(graph: AudioFlowGraph, private val flow: AudioFlow) : Edit(graph) {
            override val actionDescription: String
                get() = "Add audio flow"

            override fun doRedo() {
                graph.addFlow(flow)
            }

            override fun doUndo() {
                graph.removeFlow(flow)
            }
        }

        class RemoveFlow(graph: AudioFlowGraph, private val flow: AudioFlow) : Edit(graph) {
            override val actionDescription: String
                get() = "Remove audio flow"

            override fun doRedo() {
                graph.removeFlow(flow)
            }

            override fun doUndo() {
                graph.addFlow(flow)
            }
        }

        class MoveNode(
            graph: AudioFlowGraph,
            private val node: BusNode,
            private val fromPosition: Point2D,
            private val toPosition: Point2D
        ) : Edit(graph) {
            override val actionDescription: String
                get() = "Move Flow"

            override fun doRedo() {
                graph.move(node, toPosition)
            }

            override fun doUndo() {
                graph.move(node, fromPosition)
            }

            override fun mergeWith(other: hextant.undo.Edit): hextant.undo.Edit? =
                if (other is MoveNode && other.node == this.node && other.graph == this.graph)
                    MoveNode(graph, node, this.fromPosition, other.toPosition)
                else null
        }
    }

    interface View {
        fun addedNode(node: BusNode)

        fun removedNode(node: BusNode)

        fun addedFlow(flow: AudioFlow)

        fun removedFlow(flow: AudioFlow)

        fun movedNode(node: BusNode)
    }
}