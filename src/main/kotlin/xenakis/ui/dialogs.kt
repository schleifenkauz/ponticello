package xenakis.ui

import hextant.context.Context
import hextant.fx.registerShortcuts
import javafx.application.Platform
import javafx.geometry.Point2D
import javafx.scene.Node
import javafx.scene.Parent
import javafx.scene.control.Alert
import javafx.scene.control.Label
import javafx.scene.control.TextField
import javafx.scene.input.KeyEvent
import javafx.scene.layout.HBox
import javafx.scene.layout.VBox
import org.controlsfx.control.Notifications
import xenakis.impl.DoubleRange
import xenakis.model.ObjectRegistry
import xenakis.sc.Identifier

abstract class InputNode<R, N : Node> {
    private var commited = false
    private var result: R? = null
    private lateinit var window: SubWindow

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
        window = SubWindow(layout, title, context, SubWindow.Type.Prompt)
        window.setOnShown { onReceiveFocus() }
        window.sizeToScene()
        window.showAndWait()
        @Suppress("UNCHECKED_CAST")
        return if (commited) result as R else getDefault()
    }
}

class YesNoInput(private val question: String, private val default: Boolean = false) : InputNode<Boolean, HBox>() {
    private val btnNo = button("No") { commit(false) } styleClass "sleek-button"
    private val btnYes = button("Yes") { commit(true) } styleClass "sleek-button"
    override val content = HBox(btnNo, btnYes) styleClass "buttons-bar"
    override val title: String
        get() = question

    init {
        content.registerShortcuts {
            on("Y") { commit(true) }
            on("N") { commit(false) }
        }
    }

    override fun onReceiveFocus() {
        if (default) btnYes.requestFocus() else btnNo.requestFocus()
    }

    override fun getDefault(): Boolean = default
}

class CancellableYesNoInput(private val question: String, private val default: Boolean?) : InputNode<Boolean?, HBox>() {
    private val btnCancel = button("Cancel") { commit(null) } styleClass "sleek-button"
    private val btnNo = button("No") { commit(false) } styleClass "sleek-button"
    private val btnYes = button("Yes") { commit(true) } styleClass "sleek-button"
    override val content = HBox(btnCancel, btnNo, btnYes) styleClass "buttons-bar"
    override val title: String
        get() = question

    init {
        content.registerShortcuts {
            on("Y") { commit(true) }
            on("N") { commit(false) }
        }
    }

    override fun onReceiveFocus() {
        when (default) {
            true -> btnYes.requestFocus()
            false -> btnNo.requestFocus()
            else -> btnCancel.requestFocus()
        }
    }

    override fun getDefault(): Boolean? = null
}

abstract class TextInput<R : Any>(final override val title: String, initialText: String) : InputNode<R?, TextField>() {
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

class PredicateTextInput(
    title: String, initialText: String, private val check: (String) -> Boolean
) : TextInput<String>(title, initialText) {
    override fun convert(text: String): String? = text.takeIf(check)
}

class SimpleTextInput(title: String, initialText: String) : TextInput<String>(title, initialText) {
    override fun convert(text: String): String = text
}

class DoubleInput(
    title: String, initialValue: Double?,
    private val range: DoubleRange = Double.MIN_VALUE..Double.MAX_VALUE
) : TextInput<Double>(title, initialValue?.toString().orEmpty()) {
    override fun convert(text: String): Double? = text.toDoubleOrNull()?.takeIf { v -> v in range }
}

class IntegerInput(
    title: String, initialValue: Int?,
    private val range: IntRange = Int.MIN_VALUE..Int.MAX_VALUE
) : TextInput<Int>(title, initialValue?.toString().orEmpty()) {
    init {
        content.registerShortcuts(KeyEvent.KEY_PRESSED) {
            on("DOWN") {
                content.text.toIntOrNull()?.let { v -> if (v - 1 in range) content.text = (v - 1).toString() }
            }
            on("UP") { content.text.toIntOrNull()?.let { v -> if (v + 1 in range) content.text = (v + 1).toString() } }
        }
    }

    override fun convert(text: String): Int? = text.toIntOrNull()?.takeIf { v -> v in range }
}

fun <T : Any> showSelectorDialog(
    context: Context, title: String,
    items: List<T>, initialValue: T? = null,
    anchor: Point2D? = null, stringConverter: (T) -> String = { it.toString() }
): T? {
    val view = object : SimpleSearchableListView<T>(items) {
        override fun extractText(option: T): String = stringConverter(option)
    }
    var value = initialValue
    view.showPopup(context, title, anchor, initialValue) { v -> value = v }
    return value
}

class NameInput(
    private val registry: ObjectRegistry<*>, title: String, initialName: String
) : TextInput<String>(title, initialName) {
    override fun convert(text: String): String? {
        if (!Identifier.isValid(text)) return null
        if (registry.has(text)) return null
        return text
    }
}

abstract class ConfirmableInput<R : Any, N : Node>(override val title: String) : InputNode<R?, N>() {
    val cancelButton = button("Cancel") { commit(null) }
    val confirmButton = button("Confirm") { commit(confirm()) }

    override fun getDefault(): R? = null

    protected abstract fun confirm(): R?

    override fun createLayout(): Parent {
        val layout = super.createLayout() as VBox
        val buttons = HBox(cancelButton, confirmButton) styleClass "buttons-bar"
        layout.children.add(buttons)
        layout.registerShortcuts {
            on("Ctrl+Enter") {
                commit(confirm())
            }
        }
        return layout
    }
}

open class CompoundInput<R : Any>(title: String) : ConfirmableInput<R, DetailPane>(title) {
    private lateinit var confirm: () -> R?

    override val content: DetailPane = DetailPane()

    fun <N : Node> addItem(name: String, node: N): N {
        content.addItem(name, node)
        return node
    }

    infix fun <N: Node> N.named(name: String): N = addItem(name, this)

    fun onConfirm(handler: () -> R?) {
        confirm = handler
    }

    override fun confirm(): R? = confirm.invoke()
}

fun <R : Any> compoundInput(title: String, body: CompoundInput<R>.() -> Unit): CompoundInput<R> {
    val input = CompoundInput<R>(title)
    input.body()
    return input
}

fun alertException(action: String, exc: Exception) = Alert(Alert.AlertType.ERROR).run {
    headerText = "Exception while: $action"
    contentText = exc.message
    show()
}

fun alertError(text: String) = Platform.runLater { Notifications.create().text(text).darkStyle().showError() }

fun notifyConfirm(message: String) =
    Platform.runLater { Notifications.create().text(message).darkStyle().showConfirm() }

fun notifyInfo(message: String) =
    Platform.runLater { Notifications.create().text(message).darkStyle().showInformation() }

fun <T : Any> tryWithAlert(actionDescription: String, action: () -> T): T? = try {
    action()
} catch (e: Exception) {
    e.printStackTrace()
    alertError("Exception while $actionDescription: ${e.message}")
    null
}