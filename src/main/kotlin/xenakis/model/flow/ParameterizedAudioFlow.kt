package xenakis.model.flow

import hextant.context.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import reaktive.value.ReactiveValue
import xenakis.impl.Decimal
import xenakis.model.obj.BusObject
import xenakis.model.obj.ParameterizedObject
import xenakis.model.player.ActiveObjectManager
import xenakis.model.player.LiveSynthUpdater
import xenakis.model.score.ObjectPosition

@Serializable
sealed class ParameterizedAudioFlow : AudioFlow(), ParameterizedObject {
    override val superColliderPrefix: String
        get() = "~flow"

    @Transient
    private lateinit var listener: LiveSynthUpdater

    final override fun activeInstances(): List<ActiveObjectManager.ActiveInstance> =
        listOf(ActiveObjectManager.ActiveInstance(this, absolutePosition = ObjectPosition.ZERO, 0))

    final override fun duration(): ReactiveValue<Decimal>? = null

    override fun initialize(context: Context, bus: BusObject) {
        super.initialize(context, bus)
        listener = LiveSynthUpdater(this)
    }

    override fun onLoadedIntoRegistry() {
        super<AudioFlow>.onAdded()
        listener.listen(controls)
    }

    override fun onRemoved() {
        super<AudioFlow>.onRemoved()
        listener.stopListening()
    }

    override fun validate(): Boolean = controls.validate()

    override fun getInputs(): Collection<BusObject> {
        return super.getInputs()
    }

    override fun getOutputs(): Collection<BusObject> {
        return super.getOutputs()
    }
}