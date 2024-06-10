package xenakis.ui

import hextant.context.Context
import hextant.fx.Stylesheets
import hextant.fx.registerShortcuts
import hextant.fx.setDefaultButton
import javafx.application.Platform
import javafx.collections.FXCollections
import javafx.scene.Node
import javafx.scene.Parent
import javafx.scene.Scene
import javafx.scene.control.*
import javafx.scene.layout.HBox
import javafx.stage.Stage
import javafx.stage.StageStyle
import javafx.util.StringConverter
import org.controlsfx.control.Notifications
import xenakis.impl.DoubleRange
import xenakis.model.ObjectRegistry
import xenakis.sc.Identifier
import java.util.concurrent.CompletableFuture

@Suppress("unused")
fun <T : Any> showSelectorDialog(
    title: String,
    context: Context,
    items: List<T>,
    initialValue: T?,
    stringConverter: (T) -> String = { it.toString() },
    onConfirmed: (T) -> Unit
) {
    val selector = ComboBox(FXCollections.observableList(items))
    selector.converter = object : StringConverter<T?>() {
        override fun toString(item: T?): String = if (item == null) "<select>" else stringConverter(item)

        override fun fromString(p0: String?): T? = null
    }
    selector.value = initialValue
    val window = SubWindow(selector, title, context, SubWindow.Type.Prompt)
    selector.setOnAction {
        window.hide()
        onConfirmed(selector.value)
    }
    window.show()
}

fun showYesNoDialog(context: Context, question: String, default: Boolean = false): Boolean {
    val future = CompletableFuture<Boolean>()
    val box = HBox(5.0)
    val window = SubWindow(box, "Yes/No", context, SubWindow.Type.Prompt)
    val no = Icon.Delete.button(action = "No") {
        future.complete(false)
        window.hide()
    }
    val yes = Icon.Delete.button(action = "No") {
        future.complete(false)
        window.hide()
    }
    val label = Label(question)
    box.children.addAll(no, label, yes)
    box.registerShortcuts {
        on("ENTER") {
            future.complete(default)
            window.hide()
        }
    }
    window.show()
    Platform.runLater {
        if (default) yes.requestFocus()
        else no.requestFocus()
    }
    window.setOnHidden { if (!future.isDone) future.complete(false) }
    return future.get()
}

fun showYesNoDialog(question: String, default: Boolean = false): Boolean {
    val alert = Alert(Alert.AlertType.CONFIRMATION, question, ButtonType.YES, ButtonType.NO)
    alert.dialogPane.scene.stylesheets.add("/xenakis/ui/style.css")
    val defaultBtn = if (default) ButtonType.YES else ButtonType.NO
    alert.setDefaultButton(defaultBtn)
    return alert.showAndWait().getOrNull() == ButtonType.YES
}

fun showNumberPrompt(
    title: String,
    range: DoubleRange,
    initialValue: Double = range.start,
    context: Context,
    onEnter: (Double) -> Unit
) = showTextPrompt(title, initialValue.toString(), context) { txt ->
    val value = txt.toDoubleOrNull()
    if (value != null && value in range) {
        onEnter(value)
        true
    } else false
}

fun showTextPrompt(title: String, initialText: String, context: Context, onEnter: (String) -> Boolean) {
    val field = TextField().styleClass("prompt", "prompt-text-field")
    field.promptText = title
    field.text = initialText
    field.selectAll()
    val window = SubWindow(field, title, context, type = SubWindow.Type.Prompt)
    window.sizeToScene()
    field.registerShortcuts {
        on("ENTER") {
            if (onEnter(field.text)) {
                window.hide()
            }
        }
    }
    window.show()
}

fun showNamePrompt(registry: ObjectRegistry<*>, defaultName: String = "", createObject: (String) -> Unit) =
    showTextPrompt("${registry.objectType} name", defaultName, registry.context) { name ->
        if (!Identifier.isValid(name)) return@showTextPrompt false
        if (registry.has(name)) return@showTextPrompt false
        createObject(name)
        true
    }

fun alertException(action: String, exc: Exception) = Alert(Alert.AlertType.ERROR).run {
    headerText = "Exception while: $action"
    contentText = exc.message
    show()
}

fun alertError(text: String) = Notifications.create().text(text).darkStyle().showError()

fun <T : Any> tryWithAlert(actionDescription: String, action: () -> T): T? = try {
    action()
} catch (e: Exception) {
    e.printStackTrace()
    alertException(actionDescription, e)
    null
}

fun <T : Any> Node.showDialog(
    title: String,
    buttonTypes: List<ButtonType> = listOf(ButtonType.OK, ButtonType.CANCEL),
    confirmButton: ButtonType = ButtonType.OK,
    style: StageStyle = StageStyle.DECORATED,
    applyStylesheets: (scene: Scene) -> Unit = {},
    extraConfig: Dialog<T>.() -> Unit = {},
    resultConverter: (btn: ButtonType) -> T? = { null }
) = Dialog<T>().run {
    initStyle(style)
    this.title = title
    applyStylesheets(dialogPane.scene)
    dialogPane.content = this@showDialog
    dialogPane.buttonTypes.setAll(buttonTypes)
    setDefaultButton(confirmButton)
    extraConfig()
    setResultConverter { btn -> if (btn != null && btn != ButtonType.CANCEL) resultConverter(btn) else null }
    showAndWait().getOrNull()
}

fun <T : Any> Node.showDialog(
    title: String,
    context: Context,
    confirmButton: ButtonType = ButtonType.OK,
    buttonTypes: List<ButtonType> = listOf(confirmButton, ButtonType.CANCEL),
    style: StageStyle = StageStyle.DECORATED,
    extraConfig: Dialog<T>.() -> Unit = {},
    resultConverter: (btn: ButtonType) -> T? = { null }
) = this.showDialog(
    title, buttonTypes, confirmButton,
    style, { scene -> context[Stylesheets].manage(scene) },
    extraConfig, resultConverter
)

@Suppress("unused")
fun Parent.showWindow(title: String, context: Context, type: SubWindow.Type): Stage {
    val window = SubWindow(this, title, context, type = type, applyStylesheets = true)
    window.sizeToScene()
    window.show()
    return window
}