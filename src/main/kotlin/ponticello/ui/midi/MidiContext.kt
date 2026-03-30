package ponticello.ui.midi

import fxutils.actions.action
import hextant.context.Context
import org.kordamp.ikonli.materialdesign2.MaterialDesignC
import reaktive.value.ReactiveBoolean
import reaktive.value.binding.`if`

interface MidiContext {
    val context: Context

    val isActive: ReactiveBoolean

    val canReceiveMidi: Boolean

    fun toggleActive()

    fun setCondition(condition: () -> Boolean)

    fun cc(index: Int, value: Int) {}

    companion object {
        val toggleActiveAction = action<MidiContext>("Toggle Activate") {
            shortcut("Ctrl+T")
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