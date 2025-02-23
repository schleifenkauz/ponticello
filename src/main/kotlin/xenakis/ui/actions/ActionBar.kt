package xenakis.ui.actions

import javafx.scene.control.Button
import javafx.scene.layout.HBox
import xenakis.ui.impl.neverHGrow

class ActionBar(actions: List<ContextualizedAction>) : HBox() {
    private val buttons = mutableMapOf<ContextualizedAction, Button>()

    init {
        styleClass.add("toolbar-part")
        neverHGrow()
        for (action in actions) {
            val button = Button()
            configureButton(button, action)
            buttons[action] = button
            children.add(button)
        }
    }
}