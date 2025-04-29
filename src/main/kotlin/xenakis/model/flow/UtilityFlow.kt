package xenakis.model.flow

import hextant.context.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import reaktive.Observer
import reaktive.value.*
import reaktive.value.binding.flatMap
import xenakis.impl.Decimal
import xenakis.impl.copy
import xenakis.impl.toDecimal
import xenakis.impl.zero
import xenakis.model.obj.BusObject
import xenakis.model.obj.BusReference
import xenakis.model.obj.ParameterizedObjectDef
import xenakis.model.obj.ReferencedSynthDefObject
import xenakis.model.registry.reference
import xenakis.model.score.ParameterControlList
import xenakis.model.score.controls.BusControl
import xenakis.model.score.controls.ValueControl
import xenakis.model.score.controls.writeSynthCode
import xenakis.sc.client.ScWriter

@Serializable
class UtilityFlow(
    private val targetRef: ReactiveVariable<BusReference>,
    private val volumeDb: ReactiveVariable<Decimal> = reactiveVariable(zero),
    private val muted: ReactiveVariable<Boolean> = reactiveVariable(false),
) : ParameterizedAudioFlow() {
    @Transient
    override val def: ParameterizedObjectDef = ReferencedSynthDefObject.get("utility")

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

    override fun writeCode(writer: ScWriter, placement: NodePlacement) {
        writer.writeSynthCode(
            this, superColliderName.removePrefix("~"), cutoff = zero, placement, latency = zero
        )
    }

    override fun getDefaultName(): ReactiveString = reactiveValue("Utility")

    companion object {
        private val MUTED_VOLUME = (-60).toDecimal()

        fun create(target: BusObject): UtilityFlow = UtilityFlow(reactiveVariable(target.reference()))
    }
}