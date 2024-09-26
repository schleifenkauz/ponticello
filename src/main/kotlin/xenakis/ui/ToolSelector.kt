package xenakis.ui

import hextant.context.Context
import javafx.scene.Node
import javafx.scene.control.ToggleButton
import javafx.scene.input.MouseEvent
import reaktive.value.now
import xenakis.impl.SelectorBar
import xenakis.model.InstrumentRegistry

class ToolSelector(private val context: Context) : SelectorBar<ToolSelector.Tool>(Tool.values().toList()) {
    override fun extractGraphic(option: Tool): Node {
        return option.icon.getView()
    }

    override fun ToggleButton.extraConfig(option: Tool) {
        setMinSize(Icon.DEFAULT_RADIUS * 2, Icon.DEFAULT_RADIUS * 2)
        setMaxSize(Icon.DEFAULT_RADIUS * 2, Icon.DEFAULT_RADIUS * 2)
        styleClass("icon-button")
        centerChildrenVertically()
        if (option in setOf(Tool.Synth, Tool.PianoRoll))
            addEventHandler(MouseEvent.MOUSE_CLICKED) { ev ->
                if (ev.clickCount >= 2) {
                    val instruments = context[InstrumentRegistry]
                    SimpleSearchableRegistryView(instruments).showPopup(
                        context, "Select instrument",
                        anchorNode = this, initialOption = instruments.selectedInstrument.now
                    ) { instr -> instruments.select(instr) }
                }
            }
    }

    enum class Tool(val icon: Icon) {
        Pointer(Icon.Pointer),
        Synth(Icon.Synth),
        Task(Icon.Code),
        Envelope(Icon.Envelope),
        Memo(Icon.Memo),
        PianoRoll(Icon.Midi),
        TempoGrid(Icon.Tempo),
        Group(Icon.Compound),
        Cut(Icon.Cut),
        AddTime(Icon.AddTime);
    }
}