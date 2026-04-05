package ponticello.model.midi

import fxutils.drag.TypedDataFormat
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import ponticello.model.flow.MidiTrackFlow
import ponticello.model.flow.NodePlacement
import ponticello.model.obj.AbstractNamedObject
import ponticello.model.obj.MidiInstrumentReference
import ponticello.model.score.ScoreObject
import ponticello.sc.client.ScWriter
import ponticello.sc.client.SuperColliderClient
import ponticello.ui.midi.MidiContext
import reaktive.value.*

@Serializable
sealed class MidiInstrument : AbstractNamedObject() {
    @Transient
    final override val name: ReactiveString = reactiveValue("midi_instrument${idCounter++}")

    private val enabled = reactiveVariable(true)

    val isEnabled: ReactiveBoolean get() = enabled

    val superColliderName: String get() = "~${name.now}"

    fun toggleEnabled() {
        setEnabled(!enabled.now)
    }

    open fun setEnabled(value: Boolean) {
        enabled.set(value)
        context[SuperColliderClient].run("$superColliderName.enabled = ${isEnabled.now}")
    }

    override fun onRemoved() {
        context[SuperColliderClient].run("$superColliderName = nil")
    }

    abstract override fun copy(): MidiInstrument

    open fun addToTrack(writer: ScWriter, track: MidiTrackFlow, placement: NodePlacement) {}

    open fun midiContext(): MidiContext? = null

    open fun referencesScoreObject(obj: ScoreObject): Boolean = false

    companion object {
        private var idCounter = 0

        val DATA_FORMAT = TypedDataFormat<MidiInstrumentReference>("ponticello/midi-instrument")
    }

}