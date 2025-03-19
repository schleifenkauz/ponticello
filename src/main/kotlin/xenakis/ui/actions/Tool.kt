package xenakis.ui.actions

import fxutils.actions.*
import hextant.context.Context
import javafx.scene.input.KeyEvent
import javafx.scene.input.MouseEvent
import org.kordamp.ikonli.codicons.Codicons
import org.kordamp.ikonli.evaicons.Evaicons
import org.kordamp.ikonli.materialdesign2.*
import reaktive.value.now
import xenakis.model.registry.InstrumentRegistry
import xenakis.model.registry.ProcessDefRegistry
import xenakis.ui.launcher.XenakisMainActivity
import xenakis.ui.registry.SimpleSearchableRegistryView

enum class Tool(config: Action.Builder<SelectorBar<Tool, Context>>.() -> Unit) : SelectorBar.Option<Context, Tool> {
    Pointer({
        shortcut("ESCAPE")
        icon(MaterialDesignC.CURSOR_DEFAULT_OUTLINE)
    }),
    Resize({
        shortcut("Alt+R")
        icon(MaterialDesignA.ARROW_EXPAND)
    }),
    Synth({
        shortcuts("Alt+I", "Ctrl+I", "Shift+I")
        icon(MaterialDesignS.SINE_WAVE)
        executes { bar, ev ->
            val context = bar.context
            when {
                ev.isControlDown() -> context[XenakisMainActivity].instrumentsWindow.show()
                (ev is MouseEvent && ev.clickCount >= 2) || (ev is KeyEvent && ev.isShiftDown) -> {
                    val instruments = context[InstrumentRegistry]
                    SimpleSearchableRegistryView(instruments, "Selected instrument def").showPopup( //TODO better placement
                        anchorNode = bar,
                        initialOption = instruments.selectedInstrument
                    ) { def -> instruments.select(def) }
                }

                else -> bar.select(Synth)
            }
        }
    }),
    Process({
        shortcuts("Alt+P", "Ctrl+P", "Shift+P")
        icon(Codicons.SERVER_PROCESS)
        executes { bar, ev ->
            when {
                ev.isControlDown() -> bar.context[XenakisMainActivity].processDefsWindow.show()
                (ev is MouseEvent && ev.clickCount >= 2) || (ev is KeyEvent && ev.isShiftDown) -> {
                    val processDefs = bar.context[ProcessDefRegistry]
                    SimpleSearchableRegistryView(processDefs, "Selected process def").showPopup(
                        anchorNode = bar,
                        initialOption = processDefs.selectedDef
                    ) { def -> processDefs.select(def) }
                }

                else -> bar.select(Process)
            }
        }
    }),
    Task({
        shortcut("Alt+T")
        icon(MaterialDesignC.CODE_BRACES)
    }),
    PianoRoll({
        shortcut("Alt+M")
        icon(MaterialDesignP.PIANO)
    }),
    Group({
        shortcut("Alt+G")
        icon(MaterialDesignG.GROUP)
        description("Select object group tool")
    }),
    TempoGrid({
        shortcut("Alt+L")
        icon(MaterialDesignM.METRONOME)
        description("Select tempo grid tool")
    }),
    Memo({
        shortcut("Alt+A")
        icon(MaterialDesignM.MESSAGE_REPLY_TEXT)
    }),
    Cut({
        shortcut("Alt+C")
        icon(Evaicons.SCISSORS)
    }),
    AddTime({
        icon(MaterialDesignP.PROGRESS_QUESTION)
        description("Select add time tool")
    });

    override val action: Action<SelectorBar<Tool, Context>> = action("Select $name tool") {
        selects(this@Tool)
        config()
    }
}