package xenakis.ui.actions

import javafx.beans.binding.Bindings
import javafx.scene.control.ButtonBase
import javafx.scene.layout.HBox
import xenakis.ui.impl.neverHGrow

open class ActionBar(style: Boolean = true) : HBox() {
    private val indices = mutableMapOf<ButtonBase, Int>()

    constructor(actions: List<ContextualizedAction>, style: Boolean = true) : this(style) {
        addActions(actions)
    }

    init {
        if (style) styleClass.add("toolbar-part")
        visibleProperty().bind(Bindings.isEmpty(children).not())
        neverHGrow()
    }

    fun addActions(actions: List<ContextualizedAction>) {
        for ((idx, action) in actions.withIndex()) {
            val button = action.makeButton()
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