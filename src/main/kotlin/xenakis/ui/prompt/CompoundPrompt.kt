package xenakis.ui.prompt

import javafx.scene.Node
import javafx.scene.layout.Priority
import javafx.scene.layout.VBox
import xenakis.ui.controls.DetailPane

open class CompoundPrompt<R : Any>(title: String) : ConfirmablePrompt<R, DetailPane>(title) {
    private lateinit var confirm: () -> R?

    final override val content: DetailPane = DetailPane()

    init {
        VBox.setVgrow(content, Priority.ALWAYS)
    }

    fun <N : Node> addItem(name: String, node: N): N {
        content.addItem(name, node)
        return node
    }

    infix fun <N : Node> N.named(name: String): N = addItem(name, this)

    fun onConfirm(handler: () -> R?) {
        confirm = handler
    }

    override fun confirm(): R? = confirm.invoke()
}