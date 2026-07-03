package ponticello.model.flow

import hextant.context.Context
import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import ponticello.impl.*
import ponticello.model.instr.BusObject
import ponticello.model.obj.AbstractContextualObject
import ponticello.model.obj.BusReference
import ponticello.model.registry.ObjectList
import ponticello.model.registry.ObjectListSerializer
import ponticello.model.registry.reference
import ponticello.model.score.controls.AttackReleaseControl
import ponticello.model.server.BusRegistry
import ponticello.sc.NumericalControlSpec
import ponticello.sc.Warp
import ponticello.sc.client.ScWriter
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
    override val active = reactiveVariable(true)

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

    fun usedBuses(): List<@Contextual BusObject> {
        val sourceBuses = components.mapNotNull { it.sourceBus.now.get() }
        val targetBus = targetBus.now.get()?.let(::listOf) ?: emptyList()
        return sourceBuses + targetBus
    }

    override fun initialize(context: Context) {
        super.initialize(context)
        components.initialize(context)
        targetBus.now.resolve(context[BusRegistry])
        components.addListener(this, initialize = false)
        for (comp in components) {
            setupComponent(comp)
        }
        if (!targetBus.now.isValid) unresolvedBuses++
        sinkObserver = targetBus.observe { _, old, new ->
            replacedBus(old, new)
            update("dest = ${new.superColliderName}")
        } and activateFilters.observe { _, _, activate -> update("filter = $activate") }
        observeMasterControls()
    }

    private fun observeMasterControls() {
        masterObserver = masterVolume.observe { _, _, vol ->
            if (!masterMute.now) {
                update("masterVolume = $vol.dbamp")
            }
        } and masterMute.observe { _, _, mute ->
            val volume = if (mute) "0" else "${masterVolume.now}.dbamp"
            update("masterVolume = $volume")
        } and monoMix.observe { _, _, mono ->
            update("monoMix = ${if (mono) "1" else "0"}")
        }
    }

    private fun getActualVolume(comp: MixerComponent) =
        if (comp.isMuted || (soloed != 0 && !comp.isSolo)) "0"
        else "${comp.volume.now}.dbamp"

    override fun added(obj: MixerComponent, idx: Int) {
        setupComponent(obj)
        updateSources()
    }

    override fun removed(obj: MixerComponent, idx: Int) {
        if (obj.isSolo) soloed--
        componentObservers.remove(obj)?.kill()
        updateSources()
    }

    private fun updateSources() {
        val (buses, volumes, pans) = getSources()
        update("setSources($buses, $volumes, $pans)")
    }

    private fun getSources(): Triple<String, String, String> {
        val resolvedComponents = components.filter { it.sourceBus.now.isResolved.now }
        val buses = resolvedComponents.joinToString(", ", "[", "]") { comp -> comp.sourceBus.now.superColliderName }
        val volumes = resolvedComponents.joinToString(", ", "[", "]") { comp -> getActualVolume(comp) }
        val pans = if (targetBus.now.get()?.channels?.now == 2) {
            resolvedComponents.joinToString(", ", "[", "]") { comp -> comp.pan.now.toString() }
        } else "nil"
        return Triple(buses, volumes, pans)
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
        update("pans = $pans")
    }

    private fun setSourceBus(old: BusReference, new: BusReference) {
        replacedBus(old, new)
        if (!isActive.now) return
        updateSources()
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
        update("volumes = $volumes")
    }

    override fun writeCode(): String = writeCode {
        val (buses, volumes, pans) = getSources()
        val masterVolume = if (masterMute.now) "0" else "${masterVolume.now}.dbamp"
        append(
            "MixerFlow('", name.now, "', ", targetBus.now.superColliderName, ", ",
            buses, ", ", volumes, ", ", pans, ", ", activateFilters.now, ", ",
            masterVolume, ", ", monoMix.now, ")"
        )
    }

    override fun ScWriter.createObject() {}

    override fun midiContext(): MidiContext = MixerMidiContext()

    override fun usesBus(bus: BusObject): Boolean =
        targetBus.now.get() == bus || components.any { it.sourceBus.now.get() == bus }

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
        override fun cc(index: Int, value: Int) {
            if (index == 0) {
                masterVolume.adjustByMidiDelta(value, VOLUME_SPEC, context, "Adjust master volume")
                return
            }
            if (index + 1 !in components.indices) return
            val comp = components[index + 1]
            comp.volume.adjustByMidiDelta(value, VOLUME_SPEC, context, "Adjust channel volume")
        }
    }

    companion object {
        const val MIN_VOLUME = -60.0
        const val MAX_VOLUME = +24.0
        val VOLUME_SPEC = NumericalControlSpec(
            default = zero, min = MIN_VOLUME.toDecimal(), max = MAX_VOLUME.toDecimal(),
            step = 0.1.toDecimal(), warp = Warp.Linear, lag = AttackReleaseControl.DEFAULT,
        )

        fun create(out: BusObject) = MixerFlow(
            reactiveVariable(out.reference()),
            MixerComponentList(mutableListOf())
        )
    }
}