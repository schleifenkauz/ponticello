package xenakis.model.flow

import bundles.PublicProperty
import bundles.publicProperty
import bundles.set
import hextant.context.Context
import hextant.core.editor.ListenerManager
import hextant.undo.UndoManager
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import reaktive.Observer
import reaktive.value.ReactiveInt
import reaktive.value.ReactiveVariable
import reaktive.value.now
import reaktive.value.reactiveVariable
import xenakis.impl.Logger
import xenakis.model.obj.AbstractContextualObject
import xenakis.model.obj.BusObject
import xenakis.model.obj.BusReference
import xenakis.model.obj.GroupObject
import xenakis.model.registry.*

@Serializable
class AudioFlows(
    private var _flows: MutableMap<BusReference, AudioFlowList>
) : NamedObjectList.Listener<BusObject>, AbstractContextualObject() {
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

    private val flows: Map<BusReference, AudioFlowList> get() = _flows

    fun all(): List<AudioFlow> = flows.values.flatten()

    override fun initialize(context: Context) {
        context[AudioFlows] = this
        super.initialize(context)
        busRegistry = context[BusRegistry]
        busRegistry.addListener(this, initialize = false)
        undoManager = context[UndoManager]
        setupDatastructures()
    }

    private fun setupDatastructures() {
        _flows = _flows.mapKeysTo(mutableMapOf()) { (ref, _) -> ref.resolve(busRegistry); ref }
        for (bus in busRegistry) {
            numFlows[bus] = reactiveVariable(0)
            _flows.getOrPut(bus.reference()) { AudioFlowList() }.initialize(context, this, bus)
        }
    }

    fun addListener(listener: Listener, initialize: Boolean = true) {
        listeners.addListener(listener)
        if (!initialize) return
        for ((_, flows) in flows) {
            for ((index, flow) in flows.withIndex()) {
                listener.addedFlow(flow, index)
                if (flow.isActive.now) listener.activatedFlow(flow)
            }
        }
    }

    fun flowsFromAndTo(bus: BusObject) = all().filter { f -> bus in f.getOutputs() || bus in f.getInputs() }

    fun associatedFlows(bus: BusObject): AudioFlowList = flows.getValue(bus.reference())

    fun indexOf(flow: AudioFlow) = associatedFlows(flow.associatedBus).indexOf(flow)

    fun addSendFlow(source: BusObject, target: BusObject): AudioFlow {
        val flow = SendFlow.createFor(source, target, context)
        addFlow(flow)
        return flow
    }

    override fun added(obj: BusObject, idx: Int) {
        if (obj !is BusObject.AudioBus) return
        numFlows[obj] = reactiveVariable(0)
        _flows[obj.reference()] = AudioFlowList().also { it.initialize(context) }
    }

    override fun removed(obj: BusObject) {
        if (obj !is BusObject.AudioBus) return
        numFlows.remove(obj)!!
        _flows.remove(obj.reference())!!
    }

    fun addFlow(flow: AudioFlow, index: Int = associatedFlows(flow.associatedBus).size) {
        flows.getValue(flow.associatedBus.reference()).add(flow, index)
    }

    fun removeFlow(flow: AudioFlow) {
        flows.getValue(flow.associatedBus.reference()).remove(flow)
    }

    private fun onAddFlow(flow: AudioFlow, idx: Int) {
        numFlows.getValue(flow.associatedBus).now++
        activationObservers[flow] = flow.isActive.observe { _, _, active ->
            if (active) listeners.notifyListeners { activatedFlow(flow) }
            else listeners.notifyListeners { deactivatedFlow(flow) }
        }
        if (flow is ScoreObjectPlaceholder) placeholders[flow.group] = flow
        listeners.notifyListeners { addedFlow(flow, idx) }
        if (flow.isActive.now) listeners.notifyListeners { activatedFlow(flow) }
    }

    private fun onRemovedFlow(flow: AudioFlow, idx: Int) {
        numFlows.getValue(flow.associatedBus).now--
        val obs = activationObservers.remove(flow)
        if (obs == null) Logger.warn("Couldn't kill activation observer of $flow", Logger.Category.AudioFlow)
        else obs.kill()
        if (flow is ScoreObjectPlaceholder) {
            placeholders.remove(flow.groupRef.get())
            context[GroupRegistry].remove(flow.group)
        }
        listeners.notifyListeners {
            removedFlow(flow)
            if (flow.isActive.now) deactivatedFlow(flow)
        }
    }

    fun moveFlow(flow: AudioFlow, index: Int) {
        flows.getValue(flow.associatedBus.reference()).move(flow, index)
    }

    class FlowReference(private val busReference: BusReference, private val index: Int) : java.io.Serializable {
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

    @Serializable(with = AudioFlowList.Serializer::class)
    @SerialName("AudioFlowList")
    class AudioFlowList(
        override val objects: MutableList<AudioFlow> = mutableListOf(),
    ) : NamedObjectList<AudioFlow>() {
        private lateinit var allFlows: AudioFlows
        private lateinit var associatedBus: BusObject

        override val objectType: String
            get() = "Flow"

        fun initialize(context: Context, allFlows: AudioFlows, bus: BusObject) {
            this.allFlows = allFlows
            associatedBus = bus
            setContext(context)
            for ((idx, obj) in objects.withIndex()) {
                obj.initialize(context, associatedBus)
                allFlows.onAddFlow(obj, idx)
            }
            if (!isEmpty()) {
                first().isFirst.set(true)
                last().isLast.set(true)
            }
        }

        override fun initializeObject(obj: AudioFlow) {
            obj.initialize(context, associatedBus)
        }

        override fun onAdded(obj: AudioFlow, idx: Int) {
            allFlows.onAddFlow(obj, idx)
            if (idx == 0) {
                obj.isFirst.set(true)
                if (size > 1) get(1).isFirst.set(false)
            }
            if (idx == size - 1) {
                obj.isLast.set(true)
                if (size > 1) get(size - 2).isLast.set(false)
            }
        }

        override fun onRemoved(obj: AudioFlow, idx: Int) {
            allFlows.onRemovedFlow(obj, idx)
            if (idx == 0 && isNotEmpty()) {
                first().isFirst.set(true)
            }
            if (idx == size && isNotEmpty()) {
                last().isLast.set(true)
            }
        }

        override fun onMoved(obj: AudioFlow, oldIdx: Int, newIdx: Int) {
            allFlows.listeners.notifyListeners { movedFlow(obj, oldIdx, newIdx) }
            if (oldIdx == 0) {
                first().isFirst.set(true)
            }
            if (oldIdx == size - 1) {
                last().isLast.set(true)
            }
            if (newIdx == 0) {
                obj.isFirst.set(true)
                get(1).isFirst.set(false)
            }
            if (newIdx == size - 1) {
                obj.isLast.set(true)
                get(size - 2).isLast.set(false)
            }
        }

        object Serializer : NamedObjectListSerializer<AudioFlow, AudioFlowList>(
            kotlinx.serialization.serializer(), ::AudioFlowList
        )
    }

    companion object: PublicProperty<AudioFlows> by publicProperty("AudioFlows") {
        fun createDefault(): AudioFlows = AudioFlows(mutableMapOf())
    }

    interface Listener {
        fun addedFlow(flow: AudioFlow, index: Int) {}

        fun removedFlow(flow: AudioFlow) {}

        fun activatedFlow(flow: AudioFlow) {}

        fun deactivatedFlow(flow: AudioFlow) {}

        fun movedFlow(flow: AudioFlow, oldIndex: Int, newIndex: Int) {}
    }
}