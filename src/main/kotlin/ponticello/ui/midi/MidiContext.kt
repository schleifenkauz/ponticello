package ponticello.ui.midi

import fxutils.actions.action
import hextant.context.Context
import org.kordamp.ikonli.materialdesign2.MaterialDesignC
import ponticello.impl.MidiPitch
import reaktive.value.ReactiveBoolean
import reaktive.value.binding.`if`

interface MidiContext {
    val context: Context

    val isActive: ReactiveBoolean

    val canReceiveMidi: Boolean

    fun toggleActive()

    fun setCondition(condition: () -> Boolean)

    fun cc(channel: Int, index: Int, value: Int) {}

    fun noteOn(channel: Int, midiPitch: MidiPitch, velocity: Int) {}

    fun noteOff(channel: Int, midiPitch: MidiPitch) {}

    companion object {
        val toggleActiveAction = action<MidiContext>("Toggle Activate") {
            shortcut("Ctrl+M")
            description { ctx ->
                `if`(
                    ctx.isActive,
                    then = { "Deactivate MIDI input" },
                    otherwise = { "Activate MIDI input" }
                )
            }
            icon(MaterialDesignC.CAR_CRUISE_CONTROL)
            enableWhen { ctx -> ctx.context[ContextualMidiReceiver].isAttached }
            toggleState(MidiContext::isActive)
            executes { ctx -> ctx.toggleActive() }
        }
    }
}