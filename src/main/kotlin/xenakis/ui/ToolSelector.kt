package xenakis.ui

import hextant.context.Context
import javafx.scene.Node
import javafx.scene.control.ToggleButton
import javafx.scene.input.MouseEvent
import reaktive.value.now
import xenakis.impl.SelectorBar
import xenakis.model.InstrumentRegistry
import xenakis.ui.ToolSelector.Tool.*

class ToolSelector(private val context: Context) : SelectorBar<ToolSelector.Tool>(Tool.entries) {
    override fun extractGraphic(option: Tool): Node {
        return option.icon.getView()
    }

    override fun ToggleButton.extraConfig(option: Tool) {
        setMinSize(Icon.DEFAULT_RADIUS * 2, Icon.DEFAULT_RADIUS * 2)
        setMaxSize(Icon.DEFAULT_RADIUS * 2, Icon.DEFAULT_RADIUS * 2)
        styleClass("icon-button")
        centerChildren()
        when (option) {
            Synth, PianoRoll -> {
                addEventHandler(MouseEvent.MOUSE_CLICKED) { ev ->
                    if (ev.isShiftDown) {
                        context[XenakisUI].instrumentsWindow.show()
                    } else if (ev.clickCount >= 2) {
                        val instruments = context[InstrumentRegistry]
                        SimpleSearchableRegistryView(instruments, "Select instrument").showPopup(
                            context, anchorNode = this,
                            initialOption = instruments.selectedInstrument.now
                        ) { instr -> instruments.select(instr) }
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
    } + "(${option.ordinal})"

    enum class Tool(val icon: Icon) {
        Pointer(Icon.Pointer),
        Synth(Icon.Synth),
        Task(Icon.Code),
        Memo(Icon.Memo),
        PianoRoll(Icon.Midi),
        TempoGrid(Icon.Tempo),
        Group(Icon.Compound),
        Cut(Icon.Cut),
        AddTime(Icon.AddTime);
    }
}