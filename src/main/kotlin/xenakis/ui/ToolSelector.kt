package xenakis.ui

import javafx.scene.Node
import javafx.scene.control.ToggleButton
import javafx.scene.shape.Circle
import xenakis.impl.SelectorBar

class ToolSelector : SelectorBar<ToolSelector.Tool>(Tool.values().toList()) {
    override fun extractGraphic(option: Tool): Node {
        return option.icon.getView(ICON_SIZE / 1.6)
    }

    override fun ToggleButton.extraConfig(option: Tool) {
        shape = Circle(ICON_SIZE / 2)
        setMinSize(ICON_SIZE, ICON_SIZE)
        setMaxSize(ICON_SIZE, ICON_SIZE)
        styleClass("icon-button")
    }

    enum class Tool(val icon: Icon) {
        Pointer(Icon.Pointer), Pattern(Icon.Repeat), Synth(Icon.Synth), Task(Icon.Code), Envelope(Icon.Envelope)
    }

    companion object {
        private const val ICON_SIZE = 32.0
    }
}