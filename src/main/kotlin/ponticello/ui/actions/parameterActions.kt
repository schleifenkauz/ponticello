package ponticello.ui.actions

import hextant.context.EditorControlGroup
import hextant.context.compoundEdit
import javafx.application.Platform
import javafx.scene.control.Tooltip
import javafx.util.Duration
import ponticello.impl.writeCode
import ponticello.sc.EmptyExpr
import ponticello.sc.NamedExpr
import ponticello.sc.client.SuperColliderClient
import ponticello.sc.client.eval
import ponticello.sc.editor.*
import reaktive.value.now

fun addAllNamedArguments(editor: MessageSendEditor) {
    val existingArguments = editor.arguments.result.now
        .filterIsInstance<NamedExpr>()
        .mapTo(mutableSetOf()) { arg -> arg.name.text }
    val addedEditors = mutableListOf<ScExprExpander>()
    getParameterInfo(editor) { argumentString ->
        if (argumentString.isBlank()) return@getParameterInfo
        editor.context.compoundEdit("Add all named arguments") {
            for (arg in argumentString.split(", ")) {
                val name = arg.substringBefore("=")
                if (name in existingArguments) continue
                val defaultValue = arg.substringAfter("=", missingDelimiterValue = "")
                val expander = ScExprExpander(defaultValue)
                val argumentEditor = NamedExprEditor(IdentifierEditor(name), expander)
                editor.arguments.addLast(ScExprExpander(argumentEditor))
                addedEditors.add(expander)
            }
        }
        val newFocus = addedEditors.firstOrNull { ed -> ed.result.now == EmptyExpr } ?: addedEditors.firstOrNull()
        if (newFocus != null) {
            val control = editor.context[EditorControlGroup].getViewOf(newFocus)
            control.select()
        }
    }
}

fun showParameterInfo(
    editor: ScExprEditor<*>,
    messageSend: MessageSendEditor,
    getInfoFromArgumentString: (String) -> String = { it },
) {
    getParameterInfo(messageSend) { argumentString ->
        val info = getInfoFromArgumentString(argumentString)
        val anchor = editor.context[EditorControlGroup].getViewOf(editor)
        val position = anchor.localToScreen(0.0, anchor.height + 10.0)
        val tooltip = Tooltip(info)
        tooltip.isAutoHide = true
        tooltip.showDuration = Duration.seconds(5.0)
        tooltip.show(anchor, position.x, position.y)
        anchor.requestFocus()
    }
}

private fun getParameterInfo(
    messageSend: MessageSendEditor,
    action: (String) -> Unit,
) {
    val method = messageSend.method.result.now.text
    val receiver = messageSend.receiver.result.now
    val context = messageSend.context
    val code = writeCode(group = false) {
        receiver.code(writer, context)
        append(".class.findRespondingMethodFor(")
        append("\\")
        append(method)
        append(").argumentString")
    }
    context[SuperColliderClient].eval(code) { result ->
        Platform.runLater {
            action(result)
        }
    }
}