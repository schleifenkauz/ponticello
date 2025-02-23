package xenakis.ui.actions

import hextant.context.Context
import javafx.scene.Node
import javafx.scene.control.ToggleButton
import javafx.scene.input.MouseEvent
import org.kordamp.ikonli.Ikon
import org.kordamp.ikonli.codicons.Codicons
import org.kordamp.ikonli.evaicons.Evaicons
import org.kordamp.ikonli.javafx.FontIcon
import org.kordamp.ikonli.materialdesign2.*
import reaktive.value.now
import xenakis.model.registry.InstrumentRegistry
import xenakis.model.registry.ProcessDefRegistry
import xenakis.ui.actions.ToolSelector.Tool.*
import xenakis.ui.impl.SelectorBar
import xenakis.ui.impl.centerChildren
import xenakis.ui.impl.styleClass
import xenakis.ui.launcher.XenakisMainScreen
import xenakis.ui.registry.SimpleSearchableRegistryView

class ToolSelector(private val context: Context) : SelectorBar<ToolSelector.Tool>(Tool.entries) {
    override fun extractGraphic(option: Tool): Node = FontIcon(option.icon)

    override fun ToggleButton.extraConfig(option: Tool) {
        setMinSize(DEFAULT_RADIUS * 2, DEFAULT_RADIUS * 2)
        setMaxSize(DEFAULT_RADIUS * 2, DEFAULT_RADIUS * 2)
        styleClass("icon-button")
        centerChildren()
        when (option) {
            Synth, PianoRoll -> {
                addEventHandler(MouseEvent.MOUSE_CLICKED) { ev ->
                    if (ev.isShiftDown) {
                        context[XenakisMainScreen].instrumentsWindow.show()
                        ev.consume()
                    } else if (ev.clickCount >= 2) {
                        ev.consume()
                        val instruments = context[InstrumentRegistry]
                        SimpleSearchableRegistryView(instruments, "Select instrument").showPopup(
                            context, anchorNode = this,
                            initialOption = instruments.selectedInstrument.now
                        ) { instr -> instruments.select(instr) }
                    }
                }
            }

            Process -> {
                addEventHandler(MouseEvent.MOUSE_CLICKED) { ev ->
                    if (ev.isShiftDown) {
                        context[XenakisMainScreen].processDefsWindow.show()
                        ev.consume()
                    } else if (ev.clickCount >= 2) {
                        ev.consume()
                        val processDefs = context[ProcessDefRegistry]
                        SimpleSearchableRegistryView(processDefs, "Selected process def").showPopup(
                            context, anchorNode = this,
                            initialOption = processDefs.selectedDef
                        ) { def -> processDefs.select(def) }
                    }
                }
            }

            else -> {}
        }
    }

    override fun extractDescription(option: Tool): String = when (option) {
        Group -> "Object group"
        TempoGrid -> "Grid"
        AddTime -> "Add time"
        else -> option.name
    } + " (${option.ordinal})"

    enum class Tool(val icon: Ikon) {
        Pointer(MaterialDesignC.CURSOR_DEFAULT_OUTLINE),
        Resize(MaterialDesignA.ARROW_EXPAND),
        Synth(MaterialDesignS.SINE_WAVE),
        Process(Codicons.SERVER_PROCESS),
        Task(MaterialDesignC.CODE_BRACES),
        PianoRoll(MaterialDesignP.PIANO),
        Group(MaterialDesignG.GROUP),
        TempoGrid(MaterialDesignM.METRONOME),
        Memo(MaterialDesignM.MESSAGE_REPLY_TEXT),
        Cut(Evaicons.SCISSORS),
        AddTime(MaterialDesignP.PROGRESS_QUESTION);
    }
}