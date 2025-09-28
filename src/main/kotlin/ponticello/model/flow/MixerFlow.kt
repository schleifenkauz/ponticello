package ponticello.model.flow

import hextant.context.Context
import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import ponticello.impl.*
import ponticello.model.obj.AbstractContextualObject
import ponticello.model.obj.BusObject
import ponticello.model.obj.BusReference
import ponticello.model.registry.BusRegistry
import ponticello.model.registry.ObjectList
import ponticello.model.registry.ObjectListSerializer
import ponticello.model.registry.reference
import ponticello.model.score.controls.AttackReleaseControl
import ponticello.model.score.controls.guardAgainstReplaceNil
import ponticello.sc.NumericalControlSpec
import ponticello.sc.Warp
import ponticello.sc.client.SuperColliderClient
import ponticello.ui.midi.AbstractMidiContext
import ponticello.ui.midi.MidiContext
import ponticello.ui.midi.adjustByMidiDelta
import reaktive.Observer
import reaktive.and
import reaktive.value.*
import reaktive.value.binding.flatMap

@Serializable
@SerialName("MixerFlow")
class MixerFlow(
    val targetBus: ReactiveVariable<BusReference>,
    val components: MixerComponentList,
    val masterVolume: ReactiveVariable<Decimal> = reactiveVariable(zero),
    val masterMute: ReactiveVariable<Boolean> = reactiveVariable(false),
    val monoMix: ReactiveVariable<Boolean> = reactiveVariable(false),
    val activateFilters: ReactiveVariable<Boolean> = reactiveVariable(false)
) : AudioFlow(), ObjectList.Listener<MixerFlow.MixerComponent> {
    @SerialName("name")
    override var _name: ReactiveVariable<String>? = null

    @Transient
    private val componentObservers = mutableMapOf<MixerComponent, Observer>()

    @Transient
    private lateinit var sinkObserver: Observer

    @Transient
    private lateinit var masterObserver: Observer

    val targetChannels by lazy { targetBus.flatMap { bus -> bus.get()?.channels ?: reactiveValue(0) } }

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

    fun usedBuses(): List<@Contextual BusObject> {
        val sourceBuses = components.mapNotNull { it.sourceBus.now.get() }
        val targetBus = targetBus.now.get()?.let(::listOf) ?: emptyList()
        return sourceBuses + targetBus
    }

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
        } and activateFilters.observe { _ -> sync() }
        observeMasterControls()
    }

    private fun observeMasterControls() {
        masterObserver = masterVolume.observe { _, _, vol ->
            if (!masterMute.now) {
                client.run("$superColliderName.set(\\master_volume, $vol.dbamp)")
            }
        } and masterMute.observe { _, _, mute ->
            val volume = if (mute) "0" else "${masterVolume.now}.dbamp"
            client.run("$superColliderName.set(\\master_volume, $volume)")
        } and monoMix.observe { _, _, mono ->
            client.run("$superColliderName.set(\\mono_mix, ${if (mono) "1" else "0"})")
        }
    }

    private fun getActualVolume(comp: MixerComponent) =
        if (comp.isMuted || (soloed != 0 && !comp.isSolo)) "0"
        else "${comp.volume.now}.dbamp"

    override fun added(obj: MixerComponent, idx: Int) {
        sync()
        setupComponent(obj)
    }

    private fun setupComponent(obj: MixerComponent) {
        if (obj.isSolo) soloed++
        componentObservers[obj] = obj.sourceBus.observe { _, old, new ->
            setSourceBus(old, new)
        } and obj.state.observe { _, before, after ->
            if (before == MixerComponentMode.Solo) {
                soloed--
            }
            if (after == MixerComponentMode.Solo) {
                soloed++
            }
            recomputeVolumes()
        } and obj.volume.observe { _, _, _ ->
            recomputeVolumes()
        } and obj.pan.observe { _, _, _ ->
            panChanged()
        }
    }

    private fun panChanged() {
        if (!isActive.now) return
        val pans = components.map { comp -> (comp.pan.now / 100).withPrecision(2) }
        client.run("$superColliderName.setn(\\pans, $pans)")
    }

    private fun setSourceBus(old: BusReference, new: BusReference) {
        replacedBus(old, new)
        if (!isActive.now) return
        val buses = components.map { comp -> comp.sourceBus.now.force().superColliderName }
        client.run("$superColliderName.setn(\\sources, $buses)")
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

    override fun removed(obj: MixerComponent, idx: Int) {
        sync()
        if (obj.isSolo) soloed--
        componentObservers.remove(obj)?.kill()
    }

    override fun writeCode(placement: NodePlacement): String = writeCode {
        if (components.isEmpty()) return@writeCode
        val sink = targetBus.now.force()
        appendBlock("$superColliderName = ", endLine = false) {
            +"var sources, volumes, pans, filters, snd, mono_mix"
            val sources = components.map { comp -> comp.sourceBus.now.force().superColliderName }
            val volumes = components.map { comp -> getActualVolume(comp) }
            +"sources = NamedControl.kr(\\sources, $sources, lags: ${AttackReleaseControl.DEFAULT}, fixedLag: true)"
            +"volumes = NamedControl.kr(\\volumes, $volumes, lags: ${AttackReleaseControl.DEFAULT}, fixedLag: true)"
            if (activateFilters.now) {
                val filters = List(components.size) { "0.0" }
                +"filters = NamedControl.kr(\\filters, $filters, lags: ${AttackReleaseControl.DEFAULT}, fixedLag: true)"
            }
            +"sources = In.ar(sources, ${sink.channels.now}) * volumes"
            if (sink.channels.now == 2) {
                val pans = components.map { comp -> (comp.pan.now / 100).withPrecision(2) }
                +"pans = NamedControl.kr(\\pans, $pans, lags: ${AttackReleaseControl.DEFAULT}, fixedLag: true)"
                when (sources.size) {
                    0 -> {}
                    1 -> +"sources = Balance2.ar(sources[0], sources[1], pans)"
                    else -> {
                        for (i in sources.indices) {
                            +"sources[$i] = Balance2.ar(sources[$i][0], sources[$i][1], pans[$i])"
                        }
                    }
                }
            }
            if (activateFilters.now) {
                for (i in sources.indices) {
                    appendBlock(endLine = false) {
                        +"var dry, cutoff, lpf, hpf, filtered"
                        +"dry = sources[$i]"
                        +"lpf = BLowPass4.ar(dry, filters[$i].abs.linexp(0, 1, 20000, 60), 0.5)"
                        +"hpf = BHiPass4.ar(dry, filters[$i].abs.linexp(0, 1, 20, 12000), 0.5)"
                        +"filtered = SelectX.ar(filters[$i].linlin(-1, 1, 0, 1) ! 2, [lpf, hpf])"
                        +"sources[$i] = XFade2.ar(dry, filtered, filters[$i].abs)"
                    }
                    appendLine(".value;")
                }
            }
            +"snd = In.ar(${sink.superColliderName}, ${sink.channels.now})"
            val masterVolume =
                "\\master_volume.kr(${masterVolume.now}.dbamp, lag: ${AttackReleaseControl.DEFAULT}, fixedLag: true)"
            val mix = if (components.size > 1) "sources.sum" else "sources"
            +"snd = (snd + $mix) * $masterVolume * Linen.kr(\\gate.kr(1), 0.02, 1, 0.02, Done.freeSelf)"
            if (sink.channels.now == 2) {
                +"mono_mix = \\mono_mix.kr(${if (monoMix.now) "1" else "0"})"
                +"snd = (snd * (1 - mono_mix)) + (snd.sum / 2 ! 2 * mono_mix)"
            }
            +"ReplaceOut.ar(${sink.superColliderName}, snd)"
            +"0"
        }
        val action = guardAgainstReplaceNil(placement)
        appendLine(".play(${placement.target}, ${sink.superColliderName}, addAction: ${action});")
        +"s.sync"
        +"$superColliderName.register"
        if (!isActive.now) {
            +"$superColliderName.run(false)"
        }
    }

    override fun copy(): AudioFlow = MixerFlow(targetBus.copy(), MixerComponentList(components.toMutableList()))

    enum class MixerComponentMode {
        Regular, Mute, Solo;
    }

    @Serializable
    class MixerComponent(
        val sourceBus: ReactiveVariable<BusReference>,
        val volume: ReactiveVariable<Decimal> = reactiveVariable(zero),
        val state: ReactiveVariable<MixerComponentMode> = reactiveVariable(MixerComponentMode.Regular),
        val pan: ReactiveVariable<Decimal> = reactiveVariable(zero),
    ) : AbstractContextualObject() {
        val isMuted get() = state.now == MixerComponentMode.Mute
        val isSolo get() = state.now == MixerComponentMode.Solo

        fun copy() = MixerComponent(sourceBus, volume.copy(), state.copy(), pan.copy())

        override fun initialize(context: Context) {
            super.initialize(context)
            sourceBus.now.resolve(context[BusRegistry])
        }

        companion object {
            fun create(source: BusObject) = MixerComponent(sourceBus = reactiveVariable(BusReference(source)))
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
            if (index == 0) {
                masterVolume.adjustByMidiDelta(value, VOLUME_SPEC, context)
                return
            }
            if (channel + 1 !in components.indices) return
            val comp = components[channel + 1]
            comp.volume.adjustByMidiDelta(value, VOLUME_SPEC, context)
        }
    }

    override fun midiContext(): MidiContext = MixerMidiContext()

    override fun usesBus(bus: BusObject): Boolean =
        targetBus.now.get() == bus || components.any { it.sourceBus.now.get() == bus }

    companion object {
        const val MIN_VOLUME = -60.0
        const val MAX_VOLUME = +24.0
        val VOLUME_SPEC = NumericalControlSpec(
            default = zero, min = MIN_VOLUME.toDecimal(), max = MAX_VOLUME.toDecimal(),
            lag = AttackReleaseControl.DEFAULT, warp = Warp.Linear, step = 0.1.toDecimal(),
        )

        fun create(out: BusObject) = MixerFlow(
            reactiveVariable(out.reference()),
            MixerComponentList(mutableListOf())
        )
    }
}