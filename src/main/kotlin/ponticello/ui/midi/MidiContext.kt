package ponticello.ui.midi

import fxutils.actions.action
import fxutils.prompt.nextToTarget
import hextant.context.Context
import javafx.scene.input.MouseButton
import javafx.scene.input.MouseEvent
import org.kordamp.ikonli.materialdesign2.MaterialDesignC
import ponticello.model.midi.MidiDeviceSpec
import ponticello.model.obj.project
import ponticello.model.project.PLAYBACK_SETTINGS
import ponticello.model.project.get
import ponticello.ui.impl.defaultPlacement
import reaktive.value.ReactiveBoolean
import reaktive.value.binding.and
import reaktive.value.binding.`if`
import reaktive.value.now
import reaktive.value.reactiveValue

interface MidiContext {
    val context: Context

    val isActive: ReactiveBoolean

    val canReceiveMidi: Boolean

    fun toggleActive()

    fun setCondition(condition: () -> Boolean)

    fun cc(index: Int, value: Int) {}

    companion object {
        val toggleActiveAction = action<MidiContext>("Toggle Activate") {
            description { ctx ->
                `if`(
                    ctx.isActive,
                    then = `if`(
                        ctx.context[ContextualMidiReceiver].isAttached,
                        then = { "Deactivate MIDI input" },
                        otherwise = { "Select MIDI input" }
                    ),
                    otherwise = reactiveValue("Activate MIDI input")
                )
            }
            icon(MaterialDesignC.CAR_CRUISE_CONTROL)
            //enableWhen { ctx -> ctx.context[ContextualMidiReceiver].isAttached }
            toggleState { ctx -> ctx.isActive and ctx.context[ContextualMidiReceiver].isAttached }
            executes { ctx, ev ->
                val receiver = ctx.context[ContextualMidiReceiver]
                val rightClick = ev is MouseEvent && ev.button == MouseButton.SECONDARY
                if (rightClick || !receiver.isAttached.now) {
                    val placement = ev?.nextToTarget() ?: ctx.context.defaultPlacement
                    val device = MidiDeviceSelectorPrompt(MidiDeviceSpec.Type.SOURCE, ctx.context)
                        .showPopup(placement) ?: return@executes
                    ctx.context.project[PLAYBACK_SETTINGS].selectKnobDevice(device)
                } else {
                    ctx.toggleActive()
                }
            }
        }
    }
}