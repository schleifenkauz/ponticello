package xenakis.ui

import javafx.scene.Node
import javafx.scene.control.ToggleButton
import xenakis.impl.SelectorBar

class ToolSelector : SelectorBar<ToolSelector.Tool>(Tool.values().toList()) {
    override fun extractGraphic(option: Tool): Node {
        return option.icon.getView()
    }

    override fun ToggleButton.extraConfig(option: Tool) {
        setMinSize(Icon.DEFAULT_RADIUS * 2, Icon.DEFAULT_RADIUS * 2)
        setMaxSize(Icon.DEFAULT_RADIUS * 2, Icon.DEFAULT_RADIUS * 2)
        styleClass("icon-button")
        centerChildrenVertically()
    }

    enum class Tool(val icon: Icon) {
        Pointer(Icon.Pointer),
        Synth(Icon.Synth),
        Task(Icon.Code),
        Envelope(Icon.Envelope),
        Memo(Icon.Memo),
        Compound(Icon.Compound),
        AddTime(Icon.AddTime);
    }

    companion object {
        private const val ICON_SIZE = 32.0
    }
}