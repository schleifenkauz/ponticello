package xenakis.ui.prompt

import hextant.context.Context
import javafx.scene.Node
import javafx.scene.Parent
import javafx.scene.control.Label
import javafx.scene.layout.VBox
import xenakis.ui.SubWindow
import xenakis.ui.styleClass

abstract class Prompt<R, N : Node> {
    private var commited = false
    private var result: R? = null
    protected lateinit var window: SubWindow

    protected abstract val content: N

    protected abstract val title: String

    protected fun commit(result: R) {
        commited = true
        this.result = result
        window.hide()
    }

    protected abstract fun getDefault(): R

    protected open fun onReceiveFocus() {
        content.requestFocus()
    }

    protected open fun createLayout(): Parent = VBox(
        Label(title) styleClass "dialog-title",
        content
    ) styleClass "dialog-box"

    fun showDialog(context: Context): R {
        commited = false
        val layout = createLayout()
        window = SubWindow(layout, title, context, SubWindow.Type.Popup)
        window.setOnShown { onReceiveFocus() }
        window.sizeToScene()
        window.showAndWait()
        @Suppress("UNCHECKED_CAST")
        return if (commited) result as R else getDefault()
    }
}