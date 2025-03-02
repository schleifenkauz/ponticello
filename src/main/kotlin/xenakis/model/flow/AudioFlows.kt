package xenakis.model.flow

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
import xenakis.impl.zero
import xenakis.model.XenakisProject
import xenakis.model.obj.BusObject
import xenakis.model.registry.BusRegistry
import xenakis.model.registry.ObjectRegistry
import xenakis.sc.editor.CodeBlockEditor
import xenakis.sc.editor.IdentifierEditor
import xenakis.sc.editor.assign
import xenakis.sc.editor.`in`

@Serializable
class AudioFlows(private val _flows: MutableList<AudioFlow>) :
    ObjectRegistry.Listener<BusObject>, XenakisProject.ProjectComponent {
    @Transient
    private lateinit var registry: BusRegistry

    val context get() = registry.context

    @Transient
    private lateinit var undoManager: UndoManager

    @Transient
    val views = ListenerManager.createWeakListenerManager<View>()

    @Transient
    private val nodeNameObservers = mutableMapOf<BusObject, Observer>()

    override val componentName: String
        get() = "flow_graph"

    val flows: List<AudioFlow> get() = _flows

    fun all() = flows

    fun flowsAssociatedWith(bus: BusObject) = flows.filter { f -> f.associatedBus == bus }

    fun initialize(context: Context) {
        registry = context[BusRegistry]
        registry.addListener(this)
        undoManager = context[UndoManager]
        for (flow in flows) {
            flow.initialize(context)
        }
    }

    fun anyBusSoloed() = flows.any { f -> f is UtilityFlow && f.isActive.now && f.solo.now }

    fun addSendFlow(source: BusObject, target: BusObject): AudioFlow? {
        val flow = SendFlow(source.reference(), reactiveVariable(target.reference()), reactiveVariable(zero(1)))
        return if (addFlow(flow)) flow
        else null
    }

    fun addAdhocFlow(associatedBus: BusObject): AudioFlow? {
        val codeEditor = CodeBlockEditor(context)
        val flow = CodeFlow(associatedBus.reference(), EditorRoot.create(codeEditor))
        context.withoutUndo {
            codeEditor.variables.addLast(IdentifierEditor(context, "snd"))
            codeEditor.statements.addLast(assign("snd", `in`(context, associatedBus)))
        }
        return if (addFlow(flow)) flow
        else null
    }

    fun addFlow(flow: AudioFlow): Boolean {
        _flows.add(flow)
        undoManager.record(Edit.AddFlow(this, flow))
        views.notifyListeners { addedFlow(flow) }
        return true
    }

    fun removeFlow(flow: AudioFlow) {
        _flows.remove(flow)
//        client.run {
//            +"${flow.synthName}.free"
//            +"${flow.synthName} = nil"
//            redefineAudioFlow(this)
//        }
        undoManager.record(Edit.RemoveFlow(this, flow))
        views.notifyListeners { removedFlow(flow) }
    }

    fun updateFlow() {
//        client.run { redefineAudioFlow(this) }
    }



    override fun added(obj: BusObject, idx: Int) {
//        nodeNameObservers[obj] = obj.name.observe { _, oldName, _ -> renamedNode(obj, oldName) }
    }

    private fun getPositionForNew(bus: BusObject): Point = TODO()

    override fun removed(obj: BusObject, idx: Int) {
        //TODO what todo when a bus object is removed???
    }



    fun move(node: BusObject, position: Point) {
        val oldPosition = node.positionInGraph.now
        undoManager.record(Edit.MoveNode(this, node, oldPosition, position))
        node.positionInGraph.now = position
        views.notifyListeners { movedNode(node) }
    }

    fun flowsFromAndTo(bus: BusObject) =
        flows.filter { f -> bus in f.getConnectedBusses(*FlowType.all) }

    fun associatedFlows(bus: BusObject) = flows.filter { f -> f.associatedBus == bus }.sortedBy { f -> f.index }

    companion object {
        fun createDefault(): AudioFlows = AudioFlows(mutableListOf())
    }

    private sealed class Edit(protected val graph: AudioFlows) : AbstractEdit() {
        class AddFlow(graph: AudioFlows, private val flow: AudioFlow) : Edit(graph) {
            override val actionDescription: String
                get() = "Add audio flow"

            override fun doRedo() {
                graph.addFlow(flow)
            }

            override fun doUndo() {
                graph.removeFlow(flow)
            }
        }

        class RemoveFlow(graph: AudioFlows, private val flow: AudioFlow) : Edit(graph) {
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
            graph: AudioFlows,
            private val node: BusObject,
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
        fun addedNode(node: BusObject) {}

        fun removedNode(node: BusObject) {}

        fun addedFlow(flow: AudioFlow)

        fun removedFlow(flow: AudioFlow)

        fun movedNode(node: BusObject) {}
    }
}