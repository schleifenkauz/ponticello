package xenakis.ui.actions

import javafx.beans.binding.Bindings
import javafx.scene.control.ButtonBase
import javafx.scene.layout.HBox
import xenakis.ui.impl.neverHGrow

open class ActionBar(border: Boolean = true, private val buttonStyle: String = "tool-button") : HBox() {
    private val indices = mutableMapOf<ButtonBase, Int>()

    constructor(
        actions: List<ContextualizedAction>, border: Boolean = true, buttonStyle: String = "tool-button",
    ) : this(border, buttonStyle) {
        addActions(actions)
    }

    init {
        if (border) styleClass.add("toolbar-part")
        visibleProperty().bind(Bindings.isEmpty(children).not())
        neverHGrow()
    }

    fun addActions(actions: List<ContextualizedAction>) {
        for ((idx, action) in actions.withIndex()) {
            val button = action.makeButton(buttonStyle)
            indices[button] = idx
            if (button.isVisible) children.add(button)
            button.visibleProperty().addListener { _, _, visible ->
                if (visible) {
                    var index = children.binarySearchBy(idx) { btn -> indices[btn] }
                    if (index >= 0) return@addListener
                    index = -(index + 1)
                    children.add(index, button)
                } else children.remove(button)
            }
        }
    }
}