package xenakis.model.flow

import hextant.context.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import reaktive.value.ReactiveValue
import reaktive.value.now
import xenakis.impl.Decimal
import xenakis.model.obj.ParameterizedObject
import xenakis.model.player.ActiveAudioFlow
import xenakis.model.player.ActiveObject
import xenakis.model.player.LiveSynthUpdater
import xenakis.ui.midi.MidiContext
import xenakis.ui.midi.ParameterControlsMidiContext

@Serializable
sealed class ParameterizedAudioFlow : AudioFlow(), ParameterizedObject {
    override val superColliderPrefix: String
        get() = "~"

    @Transient
    private lateinit var listener: LiveSynthUpdater

    final override fun activeObjects(): List<ActiveObject> =
        if (isActive.now) listOf(ActiveAudioFlow(this)) else emptyList()

    final override fun duration(): ReactiveValue<Decimal>? = null

    override fun initialize(context: Context) {
        super.initialize(context)
        listener = LiveSynthUpdater(this)
    }

    override fun onLoadedIntoRegistry() {
        super<AudioFlow>.onAdded()
        listener.startListening()
    }

    override fun onRemoved() {
        super<AudioFlow>.onRemoved()
        listener.stopListening()
    }

    override fun midiContext(): MidiContext? = ParameterControlsMidiContext(controls)
}