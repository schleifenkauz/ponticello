package xenakis.model.flow

import hextant.context.Context
import hextant.core.editor.ListenerManager
import hextant.undo.UndoManager
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import reaktive.Observer
import reaktive.value.ReactiveInt
import reaktive.value.ReactiveVariable
import reaktive.value.now
import reaktive.value.reactiveVariable
import xenakis.impl.Point
import xenakis.model.Logger
import xenakis.model.XenakisProject
import xenakis.model.obj.AbstractContextualObject
import xenakis.model.obj.BusObject
import xenakis.model.obj.GroupObject
import xenakis.model.registry.BusRegistry
import xenakis.model.registry.GroupRegistry
import xenakis.model.registry.ObjectReference
import xenakis.model.registry.ObjectRegistry

@Serializable
class AudioFlows(
    private val _flows: MutableMap<ObjectReference, MutableList<AudioFlow>>
) : ObjectRegistry.Listener<BusObject>, XenakisProject.ProjectComponent, AbstractContextualObject() {
    @Transient
    private lateinit var busRegistry: BusRegistry

    @Transient
    private lateinit var undoManager: UndoManager

    @Transient
    val listeners = ListenerManager.createWeakListenerManager<Listener>()

    @Transient
    private val numFlows = mutableMapOf<BusObject, ReactiveVariable<Int>>()

    @Transient
    private val placeholders = mutableMapOf<GroupObject, ScoreObjectPlaceholder>()

    @Transient
    private val activationObservers = mutableMapOf<AudioFlow, Observer>()

    override val componentName: String
        get() = "flow_graph"

    @Transient
    private val flowMap = mutableMapOf<BusObject, MutableList<AudioFlow>>()

    private val flows: Map<BusObject, List<AudioFlow>> get() = flowMap

    fun all(): List<AudioFlow> = flows.values.flatten()

    override fun initialize(context: Context) {
        super.initialize(context)
        busRegistry = context[BusRegistry]
        busRegistry.addListener(this)
        undoManager = context[UndoManager]
        for ((busRef, flows) in _flows) {
            busRef.resolve(context[BusRegistry])
            val bus = busRef.get<BusObject>()
            flowMap[bus] = flows //not a copy, but a reference to the list so it only has to be modified once!
            for (flow in flows) {
                flow.initialize(context, bus)
                onAddFlow(flow)
            }
        }
    }

    fun addListener(listener: Listener) {
        listeners.addListener(listener)
        for ((index, flow) in all().withIndex()) {
            listener.addedFlow(flow, index)
            if (flow.isActive.now) listener.activatedFlow(flow)
        }
    }

    fun flowsFromAndTo(bus: BusObject) = all().filter { f -> bus in f.getOutputs() || bus in f.getInputs() }

    fun associatedFlows(bus: BusObject): List<AudioFlow> = flows.getValue(bus)

    fun indexOf(flow: AudioFlow) = associatedFlows(flow.associatedBus).indexOf(flow)

    fun addSendFlow(source: BusObject, target: BusObject): AudioFlow {
        val flow = SendFlow.createFor(source, target, context)
        addFlow(flow)
        return flow
    }

    override fun added(obj: BusObject, idx: Int) {
        if (obj !in numFlows) numFlows[obj] = reactiveVariable(0)
    }

    override fun removed(obj: BusObject, idx: Int) {
        numFlows.remove(obj)
    }

    fun addFlow(flow: AudioFlow, index: Int = numFlows.getValue(flow.associatedBus).now) {
        flowMap.getOrPut(flow.associatedBus, ::mutableListOf).add(index, flow)
        onAddFlow(flow)
        undoManager.record(AudioFlowsEdit.AddFlow(this, flow))
        listeners.notifyListeners {
            addedFlow(flow, index)
            if (flow.isActive.now) activatedFlow(flow)
        }
    }

    private fun onAddFlow(flow: AudioFlow) {
        flow.isFirst.now = flow == associatedFlows(flow.associatedBus).first()
        flow.isLast.now = flow == associatedFlows(flow.associatedBus).last()
        numFlows.getOrPut(flow.associatedBus) { reactiveVariable(0) }.now++
        activationObservers[flow] = flow.isActive.observe { _, _, active ->
            if (active) listeners.notifyListeners { activatedFlow(flow) }
            else listeners.notifyListeners { deactivatedFlow(flow) }
        }
        if (flow is ScoreObjectPlaceholder) placeholders[flow.group.get<GroupObject>()] = flow
    }

    fun removeFlow(flow: AudioFlow) {
        flowMap.getValue(flow.associatedBus).remove(flow)
        numFlows.getValue(flow.associatedBus).now--
        val obs = activationObservers.remove(flow)
        if (obs == null) Logger.warn("Couldn't kill activation observer of $flow", Logger.Category.AudioFlow)
        else obs.kill()
        undoManager.record(AudioFlowsEdit.RemoveFlow(this, flow))
        if (flow is ScoreObjectPlaceholder) {
            placeholders.remove(flow.group.get())
            context[GroupRegistry].remove(flow.group.get())
        }
        listeners.notifyListeners {
            removedFlow(flow)
            if (flow.isActive.now) deactivatedFlow(flow)
        }
    }

    fun move(node: BusObject, position: Point) {
        val oldPosition = node.positionInGraph.now
        undoManager.record(AudioFlowsEdit.MoveBusNode(this, node, oldPosition, position))
        node.positionInGraph.now = position
        listeners.notifyListeners { movedNode(node) }
    }

    fun forceSync() {
        TODO("Not yet implemented")
    }

    fun moveFlow(flow: AudioFlow, index: Int) {
        val associatedFlows = flowMap.getValue(flow.associatedBus)
        check(index in associatedFlows.indices) { "Invalid index $index ($flow)" }
        val oldIndex = associatedFlows.indexOf(flow)
        if (index == oldIndex) return
        associatedFlows.removeAt(oldIndex)
        associatedFlows.add(index, flow)
        flow.isFirst.now = index == 0
        flow.isLast.now = index == associatedFlows.size - 1
        undoManager.record(AudioFlowsEdit.MoveFlow(this, flow, oldIndex, index))
        listeners.notifyListeners { movedFlow(flow, oldIndex, index) }
    }

    class FlowReference(private val busReference: ObjectReference, private val index: Int) : java.io.Serializable {
        fun getFrom(flows: AudioFlows): AudioFlow {
            busReference.resolve(flows.busRegistry)
            return flows.flows.getValue(busReference.get())[index]
        }
    }

    fun referenceIndex(flow: AudioFlow): FlowReference {
        val bus = flow.associatedBus
        val index = associatedFlows(bus).indexOf(flow)
        return FlowReference(bus.reference(), index)
    }

    fun getPlaceholderNode(group: GroupObject): ScoreObjectPlaceholder = placeholders.getValue(group)

    fun numberOfFlows(associatedBus: BusObject): ReactiveInt = numFlows.getValue(associatedBus)

    companion object {
        fun createDefault(): AudioFlows = AudioFlows(mutableMapOf())
    }

    interface Listener {
        fun addedFlow(flow: AudioFlow, index: Int) {}

        fun removedFlow(flow: AudioFlow) {}

        fun activatedFlow(flow: AudioFlow) {}

        fun deactivatedFlow(flow: AudioFlow) {}

        fun movedFlow(flow: AudioFlow, oldIndex: Int, newIndex: Int) {}

        fun movedNode(node: BusObject) {}
    }
}