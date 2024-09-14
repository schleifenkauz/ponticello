package xenakis.model

import hextant.context.Context
import hextant.context.withoutUndo
import hextant.core.editor.ListenerManager
import hextant.serial.EditorRoot
import hextant.undo.AbstractEdit
import hextant.undo.UndoManager
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import reaktive.Observer
import reaktive.value.now
import reaktive.value.reactiveVariable
import xenakis.impl.Point
import xenakis.impl.ScWriter
import xenakis.impl.SuperColliderClient
import xenakis.sc.editor.*
import java.util.*

@Serializable
class AudioFlowGraph(
    private val _nodes: MutableList<BusNode>,
    private val _flows: MutableList<AudioFlow>,
) : ObjectRegistry.View<BusObject> {
    @Transient
    private lateinit var registry: BusRegistry

    @Transient
    private lateinit var client: SuperColliderClient

    @Transient
    private lateinit var undoManager: UndoManager

    @Transient
    val views = ListenerManager.createWeakListenerManager<View>()

    @Transient
    private val nodeNameObservers = mutableMapOf<BusObject, Observer>()

    val flows: List<AudioFlow> get() = _flows
    val nodes: List<BusNode> get() = _nodes

    fun initialize(context: Context) {
        registry = context[BusRegistry]
        client = context[SuperColliderClient]
        registry.addView(this)
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
        val context = registry.context
        val synth = AdhocSynthEditor(context)
        context.withoutUndo {
            val sourceBus = source.ref.get<BusObject>()
            val targetBus = target.ref.get<BusObject>()
            synth.name.setText("flow_${sourceBus.name.now}_${target.ref.get<BusObject>().name.now}")
            synth.block.variables.addLast(IdentifierEditor(context, "snd"))
            val getIn = ScExprExpander(
                context, AssignmentEditor(
                    context, IdentifierEditor(context, "snd"), ScExprExpander(
                        context, MessageSendEditor(
                            context,
                            ScExprExpander(context, "In"),
                            IdentifierEditor(context, sourceBus.rate.get().toString()),
                            ScExprListEditor(
                                context,
                                ScExprExpander(context, BusSelector(context, selected = reactiveVariable(source.ref))),
                                ScExprExpander(context, sourceBus.channels.now.toString())
                            )
                        )
                    )
                )
            )
            synth.block.statements.addLast(getIn)
            val writeOut = ScExprExpander(
                context, MessageSendEditor(
                    context,
                    ScExprExpander(context, "Out"),
                    IdentifierEditor(context, targetBus.rate.get().toString()),
                    ScExprListEditor(
                        context,
                        ScExprExpander(context, BusSelector(context, selected = reactiveVariable(target.ref))),
                        ScExprExpander(context, "snd")
                    )
                )
            )
            synth.block.statements.addLast(writeOut)
        }
        val flow = AudioFlow(source.ref, target.ref, EditorRoot.create(synth))
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
        client.run { redefineAudioFlow() }
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
            redefineAudioFlow()
        }
        undoManager.record(Edit.RemoveFlow(this, flow))
        views.notifyListeners { removedFlow(flow) }
    }

    fun updateFlow() {
        client.run { redefineAudioFlow() }
    }

    private fun ScWriter.redefineAudioFlow() {
        clearAudioFlow()
        defineAudioFlow()
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
        appendLine(";")
        +"ServerTree.add(~setup_flow)"
        +"if (s.serverRunning) { ~setup_flow.value }"

    }

    private fun ScWriter.setupAudioFlow() {
        var prev = "s.defaultGroup"
        for (flow in order) {
            val synth = flow.synth.editor.result.now
            val addAction = if (prev == "s.defaultGroup") "'addToHead'" else "'addAfter'"
            synth.writeCode(writer, registry.context, flow.synthName, prev, addAction, wrapInTask = true)
            prev = flow.synthName
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

    fun addNode(bus: ObjectReference, position: Point): Boolean {
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

    fun move(node: BusNode, position: Point) {
        undoManager.record(Edit.MoveNode(this, node, node.position, position))
        node.position = position
        views.notifyListeners { movedNode(node) }
    }

    fun removeNode(node: BusNode) {
        _nodes.remove(node)
        val associatedFlows = associatedFlows(node)
        views.notifyListeners { removedNode(node) }
        for (flow in associatedFlows) removeFlow(flow)
        nodeNameObservers.remove(node.ref.get())!!.kill()
        undoManager.record(Edit.RemoveNode(this, node, associatedFlows))
    }

    fun associatedFlows(bus: BusNode) =
        flows.filter { f -> f.source == bus.ref || f.target == bus.ref }

    private fun findFlowOrder(): List<AudioFlow>? {
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
                    q.offer(e)
                }
            }
        }
        return if (order.size == flows.size) order else null
    }

    @Serializable
    data class BusNode(val ref: ObjectReference, var position: Point)

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
            private val fromPosition: Point,
            private val toPosition: Point
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