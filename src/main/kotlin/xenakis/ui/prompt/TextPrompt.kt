package xenakis.ui.prompt

import javafx.scene.control.TextField
import xenakis.ui.impl.styleClass

abstract class TextPrompt<R : Any>(final override val title: String, initialText: String) : Prompt<R?, TextField>() {
    protected abstract fun convert(text: String): R?

    final override val content: TextField = TextField(initialText).styleClass("prompt", "prompt-text-field")

    override fun getDefault(): R? = null

    override fun onReceiveFocus() {
        content.requestFocus()
        content.selectAll()
    }

    init {
        content.setOnAction { ev ->
            val value = convert(content.text)
            if (value != null) commit(value)
            ev.consume()
        }
    }
}