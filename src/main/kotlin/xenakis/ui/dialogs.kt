package xenakis.ui

import hextant.context.Context
import hextant.fx.Stylesheets
import javafx.collections.FXCollections
import javafx.scene.Node
import javafx.scene.Parent
import javafx.scene.Scene
import javafx.scene.control.*
import javafx.scene.layout.Pane
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
    return showDialog(selector, context, confirmButton, StageStyle.TRANSPARENT) {
        selector.value.takeIf { btn -> btn == confirmButton }
    }
}

fun showDoubleInputDialog(
    title: String, context: Context,
    range: DoubleRange, initialValue: Double = 1.0,
    confirmButton: ButtonType = ButtonType.OK
) = showTextInputDialog(title, context, initialValue, confirmButton) { txt ->
    txt.toDoubleOrNull()?.takeIf { v -> v in range }
}

fun showTextInputDialog(
    title: String,
    context: Context,
    initialText: String = "",
    confirmButton: ButtonType = ButtonType.OK,
    checkText: (String) -> Boolean = { true }
): String? = showTextInputDialog<String>(title, context, initialText, confirmButton) { txt -> txt.takeIf(checkText) }

private fun <T : Any> showTextInputDialog(
    title: String,
    context: Context,
    initialValue: T,
    confirmButton: ButtonType = ButtonType.OK,
    convert: (String) -> T?
): T? = TextInputDialog(initialValue.toString()).run {
    initStyle(StageStyle.TRANSPARENT)
    headerText = ""
    contentText = title
    context[Stylesheets].manage(dialogPane.scene)
    val value = editor.textProperty().map(convert)
    dialogPane.buttonTypes.setAll(confirmButton, ButtonType.CANCEL)
    dialogPane.setDefaultButton(confirmButton, disable = value.map { it == null })
    showAndWait().getOrNull()?.let(convert)
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


fun <T : Any> showDialog(
    view: Node,
    context: Context,
    confirmButton: ButtonType = ButtonType.OK,
    style: StageStyle = StageStyle.UNDECORATED,
    resultConverter: () -> T?
) = Dialog<T>().run {
    initStyle(style)
    context[Stylesheets].manage(dialogPane.scene)
    dialogPane.content = view
    dialogPane.buttonTypes.setAll(ButtonType.CANCEL, confirmButton)
    setResultConverter { btn -> if (btn == confirmButton) resultConverter() else null }
    showAndWait().getOrNull()
}

fun Parent.makeWindow(
    title: String,
    context: Context,
    style: StageStyle = StageStyle.UNDECORATED,
    parent: Pane? = null,
    onShowing: () -> Unit = {}
): Stage {
    val stage = Stage(style)
    var idx = -1
    stage.title = title
    stage.scene = Scene(Pane())
    context[Stylesheets].manage(stage.scene)
    stage.setOnShowing {
        onShowing()
        if (parent != null) {
            idx = parent.children.indexOf(this)
            parent.children.removeAt(idx)
        }
        stage.scene.root = this
        stage.sizeToScene()
    }
    stage.setOnHidden {
        stage.scene = null
        parent?.children?.add(idx, this)
    }
    return stage
}