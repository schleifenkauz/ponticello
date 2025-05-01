package xenakis.model.flow

import hextant.context.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import reaktive.Observer
import reaktive.observe
import reaktive.value.*
import reaktive.value.binding.map
import xenakis.impl.*
import xenakis.model.obj.AbstractContextualObject
import xenakis.model.obj.BusObject
import xenakis.model.obj.BusReference
import xenakis.model.registry.BusRegistry
import xenakis.model.registry.ObjectList
import xenakis.model.registry.ObjectListSerializer
import xenakis.model.registry.ObjectReference
import xenakis.model.score.controls.guardAgainstReplaceNil
import xenakis.sc.NumericalControlSpec
import xenakis.sc.Warp
import xenakis.sc.client.SuperColliderClient
import xenakis.ui.midi.AbstractMidiContext
import xenakis.ui.midi.MidiContext

@Serializable
class MixerFlow(
    val targetBus: ReactiveVariable<BusReference>,
    val components: MixerComponentList,
) : AudioFlow(), ObjectList.Listener<MixerFlow.MixerComponent> {
    @Transient
    private val componentObservers = mutableMapOf<MixerComponent, Observer>()

    @Transient
    private lateinit var sinkObserver: Observer

    @Transient
    private var soloed = 0
        set(value) {
            val before = field
            field = value
            if (before == 0 && value > 0) {
                recomputeVolumes()
            } else if (before > 0 && value == 0) {
                recomputeVolumes()
            }
        }

    @Transient
    private var unresolvedBuses = 0
        set(value) {
            field = value
            valid.now = field == 0
        }

    @Transient
    private val valid = reactiveVariable(true)

    override val isValid: ReactiveValue<Boolean> get() = valid

    @Transient
    private lateinit var client: SuperColliderClient

    override fun initialize(context: Context) {
        client = context[SuperColliderClient]
        super.initialize(context)
        components.initialize(context)
        targetBus.now.resolve(context[BusRegistry])
        components.addListener(this, initialize = false)
        for (comp in components) setupComponent(comp)
        if (!targetBus.now.isValid) unresolvedBuses++
        sinkObserver = targetBus.observe { _, old, new ->
            replacedBus(old, new)
            sync()
        }
    }

    private fun getActualVolume(comp: MixerComponent) =
        if (comp.mute.now || (soloed != 0 && !comp.solo.now)) "0"
        else "${comp.volume.now}.dbamp"

    override fun added(obj: MixerComponent, idx: Int) {
        sync()
        setupComponent(obj)
    }

    private fun setupComponent(obj: MixerComponent) {
        if (obj.solo.now) soloed++
        componentObservers[obj] = obj.sourceBus.observe { _, old, new ->
            setSourceBus(obj, old, new)
        } and obj.mute.observe { _, _, _ ->
            recomputeVolume(obj)
        } and obj.volume.observe { _, _, _ ->
            recomputeVolume(obj)
        } and obj.solo.observe { _, _, solo ->
            if (solo) {
                soloed++
                if (soloed == 1) recomputeVolumes()
                else recomputeVolume(obj)
            } else {
                soloed--
                if (soloed == 0) recomputeVolumes()
                else recomputeVolume(obj)
            }
        }
    }

    private fun setSourceBus(comp: MixerComponent, old: BusReference, new: BusReference) {
        replacedBus(old, new)
        if (!isActive.now) return
        val idx = components.indexOf(comp)
        val bus = new.get() ?: return
        client.run("$superColliderName.set($idx, ${bus.superColliderName})") //TODO this is not right yet
    }

    private fun replacedBus(old: BusReference, new: BusReference) {
        var delta = 0
        if (!old.isResolved.now) delta--
        if (!new.isResolved.now) delta++
        unresolvedBuses += delta
    }

    private fun recomputeVolumes() {
        if (!isActive.now) return
        val volumes = components.map { comp -> getActualVolume(comp) }
        client.run("$superColliderName.setn(\\volumes, $volumes)")
    }

    private fun recomputeVolume(comp: MixerComponent) {
        if (!isActive.now) return
        recomputeVolumes()
        return //TODO how does this work
//        val argIndex = components.size + components.indexOf(comp)
//        val volume = getActualVolume(comp)
//        client.run("$superColliderName.set($argIndex, $volume)")
    }

    override fun removed(obj: MixerComponent) {
        sync()
        if (obj.solo.now) soloed--
        componentObservers.remove(obj)?.kill()
    }

    override fun writeCode(placement: NodePlacement): String = writeCode {
        if (components.isEmpty()) return@writeCode
        val sink = targetBus.now.force()
        appendBlock("$superColliderName = ", endLine = false) {
            +"var sources, volumes, mix"
            val sources = components.map { comp -> comp.sourceBus.now.force().superColliderName }
            val volumes = components.map { comp -> getActualVolume(comp) }
            +"sources = NamedControl.kr(\\sources, $sources)"
            +"volumes = NamedControl.kr(\\volumes, $volumes)"
            +"sources = In.ar(sources, ${sink.channels.now}) * volumes"
            +"sources.postln"
            +"Mix(sources).postln"
        }
        //TODO what if there is only one source bus (avoid stereo collapse)
        val action = guardAgainstReplaceNil(placement)
        appendLine(".play(${placement.target}, ${sink.superColliderName}, addAction: ${action})")
    }

    override fun getDefaultName(): ReactiveString = targetBus.map { bus -> "Mixer ${bus.getName()}" }

    override fun copy(): AudioFlow = MixerFlow(targetBus.copy(), MixerComponentList(components.toMutableList()))

    @Serializable
    class MixerComponent(
        val sourceBus: ReactiveVariable<BusReference>,
        val volume: ReactiveVariable<Decimal>,
        val mute: ReactiveVariable<Boolean>,
        val solo: ReactiveVariable<Boolean>,
        //TODO: add panning and EQ component?
    ) : AbstractContextualObject() {
        fun copy() = MixerComponent(sourceBus, volume.copy(), mute.copy(), solo.copy())

        fun observe(handler: () -> Unit): Observer =
            volume.observe(handler) and mute.observe(handler) and solo.observe(handler)

        override fun initialize(context: Context) {
            sourceBus.now.resolve(context[BusRegistry])
        }

        companion object {
            fun create(source: BusObject) = MixerComponent(
                sourceBus = reactiveVariable(BusReference(source)),
                volume = reactiveVariable(zero),
                mute = reactiveVariable(false),
                solo = reactiveVariable(false)
            )
        }
    }

    @Serializable
    class MixerComponentList(override val objects: MutableList<MixerComponent>) : ObjectList<MixerComponent>() {
        override val objectType: String
            get() = "Source Bus"

        object Serializer : ObjectListSerializer<MixerComponent, MixerComponentList>(
            MixerComponent.serializer(), ::MixerComponentList
        )
    }

    private inner class MixerMidiContext : AbstractMidiContext(context) {
        override fun cc(channel: Int, index: Int, value: Int) {
            if (channel !in components.indices) return
            val comp = components[channel]
            comp.volume.now = adjustValue(comp.volume.now, VOLUME_SPEC, value)
        }
    }

    fun midiContext(): MidiContext = MixerMidiContext()

    companion object {
        private val MUTE_VOLUME = (-1000).toDecimal()

        val VOLUME_SPEC = NumericalControlSpec(
            default = zero, min = (-60).toDecimal(), max = (+24).toDecimal(),
            warp = Warp.Linear, step = 0.1.toDecimal(),
        )

        fun create() = MixerFlow(
            reactiveVariable(ObjectReference.none()),
            MixerComponentList(mutableListOf())
        )
    }
}