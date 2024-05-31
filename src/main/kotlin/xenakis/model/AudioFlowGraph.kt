package xenakis.model

import hextant.context.Context
import hextant.core.editor.ListenerManager
import hextant.serial.EditorRoot
import hextant.undo.AbstractEdit
import hextant.undo.UndoManager
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import reaktive.value.now
import xenakis.impl.Point
import xenakis.impl.SuperColliderContext
import xenakis.sc.editor.CodeBlockEditor
import java.util.*

@Serializable
class AudioFlowGraph(
    private val _nodes: MutableList<BusNode>,
    private val _flows: MutableList<AudioFlow>,
) : ObjectRegistry.View<BusObject> {
    @Transient
    private lateinit var registry: BusRegistry

    @Transient
    private lateinit var undoManager: UndoManager

    @Transient
    val views = ListenerManager.createWeakListenerManager<View>()

    val flows: List<AudioFlow> get() = _flows
    val nodes: List<BusNode> get() = _nodes

    fun initialize(context: Context) {
        registry = context[BusRegistry]
        registry.addView(this)
        undoManager = context[UndoManager]
        for (obj in nodes) obj.ref.resolve(context)
        for (flow in flows) {
            flow.source.resolve(context)
            flow.target.resolve(context)
        }
    }

    private fun edges(node: BusObjectReference) = flows.filter { f -> f.source == node }

    fun getNode(bus: BusObjectReference) = nodes.find { it.ref == bus }
        ?: throw NoSuchElementException("No node for bus ${bus.get().name.now} found")

    @Transient
    var order = findFlowOrder() ?: error("audio flow graph contains cycles")
        private set

    fun addFlow(flow: AudioFlow): Boolean {
        if (_flows.any { f -> f.source == flow.source && f.target == flow.target }) return false
        _flows.add(flow)
        val newOrder = findFlowOrder()
        if (newOrder == null) {
            removeFlow(flow)
            return false
        }
        order = newOrder
        undoManager.record(Edit.AddFlow(this, flow))
        views.notifyListeners { addedFlow(flow) }
        return true
    }

    fun removeFlow(flow: AudioFlow) {
        _flows.remove(flow)
        undoManager.record(Edit.RemoveFlow(this, flow))
        views.notifyListeners { removedFlow(flow) }
    }

    override fun added(obj: BusObject, idx: Int) {}

    override fun removed(obj: BusObject, idx: Int) {
        for (node in nodes.toList()) {
            if (node.ref.get() == obj) {
                remove(node)
            }
        }
    }

    fun add(bus: BusObjectReference, position: Point): Boolean {
        val obj = BusNode(bus, position)
        return add(obj)
    }

    private fun add(obj: BusNode): Boolean {
        if (nodes.any { n -> n.ref == obj.ref }) {
            return false
        }
        _nodes.add(obj)
        undoManager.record(Edit.AddNode(this, obj))
        views.notifyListeners { addedNode(obj) }
        return true
    }

    fun move(node: BusNode, position: Point) {
        undoManager.record(Edit.MoveNode(this, node, node.position, position))
        node.position = position
        views.notifyListeners { movedNode(node) }
    }

    fun remove(node: BusNode) {
        _nodes.remove(node)
        val associatedFlows = associatedFlows(node)
        views.notifyListeners { removedNode(node) }
        for (flow in associatedFlows) removeFlow(flow)
        undoManager.record(Edit.RemoveNode(this, node, associatedFlows))
    }

    fun associatedFlows(bus: BusNode) =
        flows.filter { f -> f.source == bus.ref || f.target == bus.ref }

    private fun findFlowOrder(): List<AudioFlow>? {
        val q: Queue<AudioFlow> = LinkedList(flows)
        val order = mutableListOf<AudioFlow>()
        val dependencies = mutableMapOf<BusObjectReference, Int>()
        for (flow in flows) dependencies[flow.target] = (dependencies[flow.target] ?: 0) + 1
        while (q.isNotEmpty()) {
            val flow = q.poll()
            if ((dependencies[flow.source] ?: 0) == 0) {
                order.add(flow)
                for (e in edges(flow.target)) q.offer(e)
            }
        }
        return if (order.size == flows.size) order else null
    }

    fun SuperColliderContext.setupAudioFlow() = run {
        var prev = "s.defaultGroup"
        for (flow in order) {
            val source = flow.source.get()
            val target = flow.target.get()
            val ugenGraph = flow.ugenGraph.editor.result.now
            val synthName = "~flow_${source.name}_${target.name}"
            appendLine("{")
            +"var sig = In.${source.rate}(${source.variableName}, ${source.channels})"
            ugenGraph.writeCode(this)
            val addAction = if (prev == "s.defaultGroup") "addToTail" else "addAfter"
            +"}.play($prev, ${target.variableName}, addAction: '$addAction')"
            prev = synthName
        }
    }

    @Serializable
    data class BusNode(val ref: BusObjectReference, var position: Point)

    @Serializable
    class AudioFlow(
        val source: BusObjectReference,
        val target: BusObjectReference,
        val ugenGraph: EditorRoot<CodeBlockEditor>
    ) {
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
                graph.add(node)
                for (flow in associatedFlows) graph.addFlow(flow)
            }

            override fun doRedo() {
                graph.remove(node)
            }
        }

        class AddNode(graph: AudioFlowGraph, private val node: BusNode) : Edit(graph) {
            override val actionDescription: String
                get() = "Add audio flow graph node"

            override fun doUndo() {
                graph.remove(node)
            }

            override fun doRedo() {
                graph.add(node)
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