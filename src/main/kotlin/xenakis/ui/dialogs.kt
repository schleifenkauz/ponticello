package xenakis.ui

import hextant.context.Context
import hextant.fx.Stylesheets
import hextant.fx.registerShortcuts
import hextant.fx.setDefaultButton
import javafx.collections.FXCollections
import javafx.scene.Node
import javafx.scene.Parent
import javafx.scene.Scene
import javafx.scene.control.*
import javafx.stage.Stage
import javafx.stage.StageStyle
import javafx.util.StringConverter
import xenakis.impl.DoubleRange

fun <T : Any> showSelectorDialog(
    title: String,
    context: Context,
    items: List<T>,
    initialValue: T?,
    confirmButton: ButtonType = ButtonType.OK,
    stringConverter: (T) -> String = { it.toString() },
): T? = Dialog<T>().run {
    this.title = title
    val selector = ComboBox(FXCollections.observableList(items))
    selector.converter = object : StringConverter<T?>() {
        override fun toString(item: T?): String = stringConverter(item!!)

        override fun fromString(p0: String?): T? = null
    }
    selector.value = initialValue
    return selector.showDialog(
        title, context,
        confirmButton, buttonTypes = listOf(confirmButton, ButtonType.CANCEL),
        StageStyle.TRANSPARENT
    ) {
        selector.value.takeIf { btn -> btn == confirmButton }
    }
}

fun showYesNoDialog(question: String, default: Boolean = false): Boolean {
    val alert = Alert(Alert.AlertType.CONFIRMATION, question, ButtonType.YES, ButtonType.NO)
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
    val field = TextField() styleClass "prompt-text-field"
    field.promptText = title
    field.text = initialText
    field.selectAll()
    val window = SubWindow(field, title, context, type = SubWindow.Type.Prompt)
    field.registerShortcuts {
        on("ENTER") {
            if (onEnter(field.text)) {
                window.hide()
            }
        }
    }
    window.show()
}

fun alertException(action: String, exc: Exception) = Alert(Alert.AlertType.ERROR).run {
    headerText = "Exception while: $action"
    contentText = exc.message
    show()
}

fun alertError(text: String) = Alert(Alert.AlertType.ERROR, text).show()

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
    style: StageStyle = StageStyle.UNDECORATED,
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
    style: StageStyle = StageStyle.UNDECORATED,
    extraConfig: Dialog<T>.() -> Unit = {},
    resultConverter: (btn: ButtonType) -> T? = { null }
) = this.showDialog(
    title, buttonTypes, confirmButton,
    style, { scene -> context[Stylesheets].manage(scene) },
    extraConfig, resultConverter
)

fun Parent.showWindow(title: String, context: Context, type: SubWindow.Type): Stage {
    val window = SubWindow(this, title, context, type = type, applyStylesheets = true)
    window.show()
    return window
}