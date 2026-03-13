package ponticello.model.flow

import hextant.context.Context
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import ponticello.impl.Decimal
import ponticello.impl.copy
import ponticello.impl.toDecimal
import ponticello.impl.zero
import ponticello.model.instr.BusObject
import ponticello.model.instr.InstrumentObject
import ponticello.model.instr.ReferencedSynthDefObject
import ponticello.model.obj.BusReference
import ponticello.model.registry.reference
import ponticello.model.score.controls.BusControl
import ponticello.model.score.controls.ParameterControlList
import ponticello.model.score.controls.ValueControl
import reaktive.Observer
import reaktive.Reactive
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

    override fun getInstrument(): InstrumentObject = ReferencedSynthDefObject.get("utility")

    override val instrumentChanged: Reactive
        get() = reactiveVariable(Unit)

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
        getInstrument().initialize(context)
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

    companion object {
        private val MUTED_VOLUME = (-60).toDecimal()

        fun create(target: BusObject): UtilityFlow = UtilityFlow(reactiveVariable(target.reference()))
    }
}