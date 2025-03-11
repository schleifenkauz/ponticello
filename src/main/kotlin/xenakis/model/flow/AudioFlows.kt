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
    private var _flows: MutableMap<ObjectReference, MutableList<AudioFlow>>
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

    private val flows: Map<ObjectReference, List<AudioFlow>> get() = _flows

    fun all(): List<AudioFlow> = flows.values.flatten()

    override fun initialize(context: Context) {
        super.initialize(context)
        busRegistry = context[BusRegistry]
        busRegistry.addListener(this, initialize = false)
        undoManager = context[UndoManager]
        setupDatastructures()
    }

    private fun setupDatastructures() {
        for (busRef in _flows.keys) busRef.resolve(busRegistry)
        _flows = _flows.mapKeysTo(mutableMapOf()) { (ref, _) -> ref.resolve(busRegistry); ref }
        for (bus in busRegistry.all()) {
            numFlows[bus] = reactiveVariable(0)
            if (bus.reference() !in _flows) _flows[bus.reference()] = mutableListOf()
        }
        for ((bus, flows) in _flows) {
            if (flows.isEmpty()) continue
            flows.first().isFirst.set(true)
            flows.last().isLast.set(true)
            for (flow in flows) {
                flow.initialize(context, bus.get())
                onAddFlow(flow)
            }
        }
    }

    fun addListener(listener: Listener) {
        listeners.addListener(listener)
        for ((_, flows) in flows) {
            for ((index, flow) in flows.withIndex()) {
                listener.addedFlow(flow, index)
                if (flow.isActive.now) listener.activatedFlow(flow)
            }
        }
    }

    fun flowsFromAndTo(bus: BusObject) = all().filter { f -> bus in f.getOutputs() || bus in f.getInputs() }

    fun associatedFlows(bus: BusObject): List<AudioFlow> = flows.getValue(bus.reference())

    fun indexOf(flow: AudioFlow) = associatedFlows(flow.associatedBus).indexOf(flow)

    fun addSendFlow(source: BusObject, target: BusObject): AudioFlow {
        val flow = SendFlow.createFor(source, target, context)
        addFlow(flow)
        return flow
    }

    override fun added(obj: BusObject, idx: Int) {
        numFlows[obj] = reactiveVariable(0)
        _flows[obj.reference()] = mutableListOf()
    }

    override fun removed(obj: BusObject, idx: Int) {
        numFlows.remove(obj)
        _flows.remove(obj.reference())
    }

    fun addFlow(flow: AudioFlow, index: Int = numFlows.getValue(flow.associatedBus).now) {
        changeFlows(flow.associatedBus) { add(index, flow) }
        onAddFlow(flow)
        undoManager.record(AudioFlowsEdit.AddFlow(this, flow))
        listeners.notifyListeners {
            addedFlow(flow, index)
            if (flow.isActive.now) activatedFlow(flow)
        }
    }

    private fun onAddFlow(flow: AudioFlow) {
        numFlows.getValue(flow.associatedBus).now++
        activationObservers[flow] = flow.isActive.observe { _, _, active ->
            if (active) listeners.notifyListeners { activatedFlow(flow) }
            else listeners.notifyListeners { deactivatedFlow(flow) }
        }
        if (flow is ScoreObjectPlaceholder) placeholders[flow.groupRef.get<GroupObject>()] = flow
    }

    fun removeFlow(flow: AudioFlow) {
        changeFlows(flow.associatedBus) { remove(flow) }
        numFlows.getValue(flow.associatedBus).now--
        val obs = activationObservers.remove(flow)
        if (obs == null) Logger.warn("Couldn't kill activation observer of $flow", Logger.Category.AudioFlow)
        else obs.kill()
        undoManager.record(AudioFlowsEdit.RemoveFlow(this, flow))
        if (flow is ScoreObjectPlaceholder) {
            placeholders.remove(flow.groupRef.get())
            context[GroupRegistry].remove(flow.groupRef.get())
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

    fun syncAll() {
        TODO("Not yet implemented")
    }

    fun moveFlow(flow: AudioFlow, index: Int) {
        changeFlows(flow.associatedBus) {
            check(index in indices) { "Invalid index $index ($flow)" }
            val oldIndex = indexOf(flow)
            if (index == oldIndex) return
            removeAt(oldIndex)
            add(index, flow)
            undoManager.record(AudioFlowsEdit.MoveFlow(this@AudioFlows, flow, oldIndex, index))
            listeners.notifyListeners { movedFlow(flow, oldIndex, index) }
        }
    }

    private inline fun changeFlows(bus: BusObject, action: MutableList<AudioFlow>.() -> Unit) {
        val associatedFlows = _flows.getValue(bus.reference())
        associatedFlows.first().isFirst.now = false
        associatedFlows.last().isLast.now = false
        associatedFlows.action()
        associatedFlows.first().isFirst.now = true
        associatedFlows.last().isLast.now = true
    }

    class FlowReference(private val busReference: ObjectReference, private val index: Int) : java.io.Serializable {
        fun getFrom(flows: AudioFlows): AudioFlow {
            busReference.resolve(flows.busRegistry)
            return flows.flows.getValue(busReference)[index]
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