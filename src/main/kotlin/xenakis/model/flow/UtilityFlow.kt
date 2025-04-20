package xenakis.model.flow

import hextant.context.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import reaktive.value.*
import xenakis.impl.Decimal
import xenakis.impl.copy
import xenakis.impl.zero
import xenakis.model.obj.BusObject
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
    private val volumeDb: ReactiveVariable<Decimal> = reactiveVariable(zero),
    private val muted: ReactiveVariable<Boolean> = reactiveVariable(false),
    val solo: ReactiveVariable<Boolean> = reactiveVariable(false),
) : ParameterizedAudioFlow() {
    @Transient
    override val def: ParameterizedObjectDef = ReferencedSynthDefObject.get("utility")

    @Transient
    override lateinit var controls: ParameterControlList
        private set

    override fun initialize(context: Context, bus: BusObject) {
        super.initialize(context, bus)
        def.initialize(context)
        controls = ParameterControlList.create(
            "bus" to BusControl(reactiveVariable(associatedBus.reference())),
            "volume" to ValueControl(volumeDb), //TODO how to mute
        )
        controls.initialize(context, this)
    }

    override fun copy(): AudioFlow =
        UtilityFlow(volumeDb.copy(), muted.copy(), solo.copy())

    override fun ScWriter.writeCode(placement: NodePlacement) {
        //TODO we need a way to mute other buses when something is soloed
        val name = superColliderName.now
        writeSynthCode(
            this@UtilityFlow, name.removePrefix("~"),
            cutoff = zero, placement, latency = zero,
            customSynthVar = name
        )
    }

    override fun getDefaultName(): ReactiveString = reactiveValue("Utility")

    override fun getInputs(): Collection<BusObject> = emptySet()

    override fun getOutputs(): Collection<BusObject> = emptySet()

    override fun addListener(listener: AudioNode.Listener) {
    }
}