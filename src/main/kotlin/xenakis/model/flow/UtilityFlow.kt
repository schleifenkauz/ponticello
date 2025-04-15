package xenakis.model.flow

import kotlinx.serialization.Serializable
import reaktive.value.*
import xenakis.impl.Decimal
import xenakis.impl.copy
import xenakis.impl.zero
import xenakis.model.obj.BusObject
import xenakis.model.obj.ReferencedSynthDefObject
import xenakis.model.registry.reference
import xenakis.model.score.ObjectPosition
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
) : AudioFlow() {
    override fun copy(): AudioFlow =
        UtilityFlow(volumeDb.copy(), muted.copy(), solo.copy())

    override fun validate(): Boolean = true

    override fun ScWriter.writeCode(placement: NodePlacement) {
        //TODO we need a way to mute other buses when something is soloed
        val volume = when {
            muted.now -> Decimal.NINF
            else -> volumeDb.now
        }
        val controls = ParameterControlList.create(
            "bus" to BusControl(reactiveVariable(associatedBus.reference())),
            "volume" to ValueControl(reactiveVariable(volume)),
        )
        val info = ScoreObjectInfo(ObjectPosition.ZERO, suffix = 0, placement, cutoff = zero)
        val def = ReferencedSynthDefObject.get("utility")
        writeSynthCode(SynthFlow(def, controls), info, controls)
    }

    override fun getDefaultName(): ReactiveString = reactiveValue("Utility")

    override fun getInputs(): Collection<BusObject> = emptySet()

    override fun getOutputs(): Collection<BusObject> = emptySet()

    override fun addListener(listener: AudioNode.Listener) {
    }
}