package xenakis.ui

import javafx.scene.Node
import javafx.scene.control.ToggleButton
import javafx.scene.shape.Circle
import xenakis.impl.SelectorBar

class ToolSelector : SelectorBar<ToolSelector.Tool>(Tool.values().toList()) {
    override fun extractGraphic(option: Tool): Node {
        return option.icon.getView()
    }

    override fun ToggleButton.extraConfig(option: Tool) {
        shape = Circle(24.0)
        setMinSize(48.0, 48.0)
        setMaxSize(48.0, 48.0)
        styleClass("icon-button")
    }

    enum class Tool(val icon: Icon) {
        Pointer(Icon.Pointer), Pattern(Icon.Repeat), Synth(Icon.Synth), Task(Icon.Code), Envelope(Icon.Envelope)
    }
}