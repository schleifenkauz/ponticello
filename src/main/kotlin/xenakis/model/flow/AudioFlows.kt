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
import xenakis.model.registry.ObjectRegistry

@Serializable
class AudioFlows(
    private val _flows: MutableList<AudioFlow>
) : ObjectRegistry.Listener<BusObject>, XenakisProject.ProjectComponent, AbstractContextualObject() {
    @Transient
    private lateinit var registry: BusRegistry

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

    val flows: List<AudioFlow> get() = _flows

    fun all() = flows

    override fun initialize(context: Context) {
        super.initialize(context)
        registry = context[BusRegistry]
        registry.addListener(this)
        undoManager = context[UndoManager]
        for (flow in flows) {
            onAddFlow(flow)
        }
    }

    fun addListener(listener: Listener) {
        listeners.addListener(listener)
        for (flow in flows.sortedBy { f -> f.index.now }) {
            listener.addedFlow(flow)
            if (flow.isActive.now) listener.activatedFlow(flow)
        }
    }

    fun flowsFromAndTo(bus: BusObject) =
        flows.filter { f -> bus in f.getConnectedBusses(*FlowType.all) }

    fun associatedFlows(bus: BusObject) = flows.filter { f -> f.associatedBus == bus }.sortedBy { f -> f.index.now }

    fun anyBusSoloed() = flows.any { f -> f is UtilityFlow && f.isActive.now && f.solo.now }

    fun addSendFlow(source: BusObject, target: BusObject): AudioFlow {
        val flow = SendFlow.createFor(source, target)
        flow.index.now = associatedFlows(flow.associatedBus).size
        addFlow(flow)
        return flow
    }

    override fun added(obj: BusObject, idx: Int) {
        if (obj !in numFlows) numFlows[obj] = reactiveVariable(0)
    }

    override fun removed(obj: BusObject, idx: Int) {
        numFlows.remove(obj)
    }

    fun addFlow(flow: AudioFlow) {
        _flows.add(flow)
        onAddFlow(flow)
        undoManager.record(AudioFlowsEdit.AddFlow(this, flow))
        listeners.notifyListeners {
            addedFlow(flow)
            if (flow.isActive.now) activatedFlow(flow)
        }
    }

    private fun onAddFlow(flow: AudioFlow) {
        flow.initialize(context)
        for (f in associatedFlows(flow.associatedBus)) {
            if (f.index.now > flow.index.now) {
                f.index.now++
            }
        }
        numFlows.getOrPut(flow.associatedBus) { reactiveVariable(0) }.now++
        activationObservers[flow] = flow.isActive.observe { _, _, active ->
            if (active) listeners.notifyListeners { activatedFlow(flow) }
            else listeners.notifyListeners { deactivatedFlow(flow) }
        }
        if (flow is ScoreObjectPlaceholder) placeholders[flow.group] = flow
    }

    fun removeFlow(flow: AudioFlow) {
        _flows.remove(flow)
        for (f in associatedFlows(flow.associatedBus)) {
            if (f.index.now > flow.index.now) {
                f.index.now--
            }
        }
        numFlows.getValue(flow.associatedBus).now--
        val obs = activationObservers.remove(flow)
        if (obs == null) Logger.warn("Couldn't kill activation observer of $flow", Logger.Category.AudioFlow)
        else obs.kill()
        undoManager.record(AudioFlowsEdit.RemoveFlow(this, flow))
        if (flow is ScoreObjectPlaceholder) {
            placeholders.remove(flow.group)
            context[GroupRegistry].remove(flow.group)
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
        val associatedFlows = associatedFlows(flow.associatedBus)
        check(index in associatedFlows.indices) { "Invalid index $index ($flow)" }
        val oldIndex = flow.index.now
        if (index == oldIndex) return
        if (index > oldIndex) {
            for (i in oldIndex + 1..index) {
                associatedFlows[i].index.now--
            }
        } else {
            for (i in index until oldIndex) {
                associatedFlows[i].index.now++
            }
        }
        flow.index.now = index
        undoManager.record(AudioFlowsEdit.MoveFlow(this, flow, oldIndex, index))
        listeners.notifyListeners { movedFlow(flow, oldIndex) }
    }

    fun referenceIndex(flow: AudioFlow) = flows.indexOf(flow)

    fun getPlaceholderNode(group: GroupObject): ScoreObjectPlaceholder = placeholders.getValue(group)

    fun numberOfFlows(associatedBus: BusObject): ReactiveInt = numFlows.getValue(associatedBus)

    fun getFlow(referenceIndex: Int) = flows[referenceIndex]

    companion object {
        fun createDefault(): AudioFlows = AudioFlows(mutableListOf())
    }

    interface Listener {
        fun addedFlow(flow: AudioFlow) {}

        fun removedFlow(flow: AudioFlow) {}

        fun activatedFlow(flow: AudioFlow) {}

        fun deactivatedFlow(flow: AudioFlow) {}

        fun movedFlow(flow: AudioFlow, oldIndex: Int) {}

        fun movedNode(node: BusObject) {}
    }
}