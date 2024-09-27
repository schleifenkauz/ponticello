package xenakis.ui.prompt

import hextant.fx.registerShortcuts
import javafx.scene.Node
import javafx.scene.Parent
import javafx.scene.control.Button
import javafx.scene.layout.HBox
import javafx.scene.layout.VBox
import xenakis.ui.button
import xenakis.ui.styleClass

abstract class ConfirmablePrompt<R : Any, N : Node>(final override val title: String) : Prompt<R?, N>() {
    val cancelButton = button("Cancel") { commit(null) }
    val confirmButton = button("Confirm") { commit(confirm()) }

    override fun getDefault(): R? = null

    protected abstract fun confirm(): R?

    protected open fun extraButtons(): List<Button> = emptyList()

    override fun createLayout(): Parent {
        val layout = super.createLayout() as VBox
        val buttons = HBox(cancelButton, confirmButton) styleClass "buttons-bar"
        buttons.children.addAll(extraButtons())
        layout.children.add(buttons)
        layout.registerShortcuts {
            on("Ctrl+Enter") { ev ->
                if (ev.target == layout) commit(confirm())
            }
        }
        return layout
    }
}