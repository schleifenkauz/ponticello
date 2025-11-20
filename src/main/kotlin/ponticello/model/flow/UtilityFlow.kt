package ponticello.model.flow

import hextant.context.Context
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import ponticello.impl.*
import ponticello.model.instr.BusObject
import ponticello.model.instr.InstrumentObject
import ponticello.model.instr.ReferencedSynthDefObject
import ponticello.model.obj.BusReference
import ponticello.model.registry.reference
import ponticello.model.score.controls.BusControl
import ponticello.model.score.controls.ParameterControlList
import ponticello.model.score.controls.ValueControl
import ponticello.model.score.controls.writeSynthCode
import reaktive.Observer
import reaktive.value.*
import reaktive.value.binding.flatMap

@Serializable
@SerialName("UtilityFlow")
class UtilityFlow(
    private val targetRef: ReactiveVariable<BusReference>,
    private val volumeDb: ReactiveVariable<Decimal> = reactiveVariable(zero),
    private val muted: ReactiveVariable<Boolean> = reactiveVariable(false),
) : ParameterizedAudioFlow() {
    @SerialName("name")
    override var _name: ReactiveVariable<String>? = null

    @Transient
    override val def: InstrumentObject = ReferencedSynthDefObject.get("utility")

    @Transient
    private val actualVolume = reactiveVariable(volumeDb.now)

    @Transient
    private lateinit var muteObserver: Observer

    @Transient
    override lateinit var controls: ParameterControlList
        private set

    override val isValid: ReactiveValue<Boolean> = targetRef.flatMap(BusReference::isResolved)

    override fun initialize(context: Context) {
        super.initialize(context)
        def.initialize(context)
        muteObserver = muted.forEach { mute ->
            actualVolume.now = if (mute) MUTED_VOLUME else volumeDb.now
        }
        controls = ParameterControlList.create(
            "bus" to BusControl(targetRef),
            "volume" to ValueControl(actualVolume),
        )
        controls.initialize(context, this)
    }

    override fun copy(): AudioFlow =
        UtilityFlow(targetRef.copy(), volumeDb.copy(), muted.copy())

    override fun writeCode(placement: NodePlacement): String = writeCode {
        writeSynthCode(
            this@UtilityFlow, superColliderName.removePrefix("~"),
            cutoff = zero, placement, latency = zero,
            run = isActive.now
        )
    }

    companion object {
        private val MUTED_VOLUME = (-60).toDecimal()

        fun create(target: BusObject): UtilityFlow = UtilityFlow(reactiveVariable(target.reference()))
    }
}