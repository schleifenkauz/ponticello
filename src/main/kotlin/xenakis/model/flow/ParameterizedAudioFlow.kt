package xenakis.model.flow

import hextant.context.Context
import kotlinx.serialization.Transient
import reaktive.value.ReactiveValue
import reaktive.value.now
import xenakis.impl.Decimal
import xenakis.model.obj.BusObject
import xenakis.model.obj.ParameterizedObject
import xenakis.model.player.LiveSynthControlUpdater

abstract class ParameterizedAudioFlow : AudioFlow(), ParameterizedObject {
    override val superColliderPrefix: String
        get() = "~flow"

    @Transient
    private lateinit var listener: LiveSynthControlUpdater

    final override fun activeInstances(): List<String> = listOf(name.now)

    final override fun duration(): ReactiveValue<Decimal>? = null

    override fun initialize(context: Context, bus: BusObject) {
        super.initialize(context, bus)
        listener = LiveSynthControlUpdater(this)
        listener.listen(controls)
    }

    override fun validate(): Boolean = controls.validate()

    override fun getInputs(): Collection<BusObject> {
        return super.getInputs()
    }

    override fun getOutputs(): Collection<BusObject> {
        return super.getOutputs()
    }
}