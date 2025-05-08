package xenakis.ui.misc

import fxutils.*
import fxutils.actions.*
import fxutils.prompt.InfoPrompt
import fxutils.undo.UndoManager
import hextant.context.Context
import hextant.context.EditorControlGroup
import hextant.context.SelectionDistributor
import hextant.core.Editor
import hextant.core.editor.defaultState
import hextant.fx.initHextantScene
import hextant.serial.EditorRoot
import javafx.application.Platform
import javafx.geometry.Point2D
import javafx.geometry.Pos
import javafx.scene.Parent
import javafx.scene.control.ScrollPane
import javafx.scene.input.Clipboard
import javafx.scene.input.DataFormat
import javafx.scene.layout.HBox
import javafx.scene.layout.Region
import javafx.scene.layout.StackPane
import javafx.scene.layout.VBox
import javafx.scene.paint.Color
import javafx.scene.text.Text
import org.kordamp.ikonli.materialdesign2.MaterialDesignC
import org.kordamp.ikonli.materialdesign2.MaterialDesignS
import reaktive.value.now
import xenakis.impl.Logger
import xenakis.impl.writeCode
import xenakis.sc.ScExpr
import xenakis.sc.client.SuperColliderClient
import xenakis.sc.client.SuperColliderException
import xenakis.sc.editor.CodeBlockEditor
import xenakis.sc.editor.ScExprEditor
import xenakis.sc.editor.ScExprExpander
import xenakis.sc.editor.ScExprListEditor
import xenakis.ui.actions.UndoRedoActions
import xenakis.ui.impl.showDialog
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors

class CodePane(
    private val rootEditor: ScExprEditor<*>?,
    private val rootControl: Parent,
    private val context: Context,
    extraActions: List<ContextualizedAction> = emptyList(),
    actionBarAlignment: Pos = Pos.TOP_RIGHT,
    ownWindow: Boolean = false,
) : StackPane() {
    constructor(
        root: EditorRoot<out ScExprEditor<*>>,
        extraActions: List<ContextualizedAction> = emptyList(),
        actionBarAlignment: Pos = Pos.TOP_RIGHT,
        ownWindow: Boolean = false,
    ) : this(root.editor, root.control, root.editor.context, extraActions, actionBarAlignment, ownWindow)

    init {
        rootControl.styleClass("code-pane")
        registerShortcuts {
            on("Ctrl+Shift?+ENTER") { ev ->
                executeSelectedCode(delete = ev.isShiftDown)
            }
        }
        if (ownWindow) {
            val actions = actions.withContext(this) + extraActions
            val actionBar = ActionBar(actions, "medium-icon-button").floating(actionBarAlignment)
            val scrollPane = ScrollPane(rootControl)
            setAlignment(scrollPane, Pos.TOP_LEFT)
            children.addAll(scrollPane, actionBar)
            registerShortcuts(actions)
        } else {
            children.add(rootControl)
            registerShortcuts(listOf(evaluateSelectedCodeAction.withContext(this)))
        }
    }

    override fun requestFocus() {
        rootControl.requestFocus()
    }

    private fun executeSelectedCode(delete: Boolean) {
        val selectedTargets = context[SelectionDistributor].selectedTargets.now.toList()
        if (selectedTargets.isEmpty()) return
        val selectedEditors = selectedTargets.filterIsInstance<ScExprEditor<*>>()
        val parents = selectedEditors.mapNotNull { it.parent }.distinct()
        val singleParent = parents.singleOrNull()
        if (selectedEditors.size > 1 && singleParent is ScExprListEditor && singleParent.parent is CodeBlockEditor) {
            evaluateConsecutiveStatements(selectedEditors, singleParent, delete)
        } else if (selectedTargets.size == 1) {
            val editor = selectedTargets.single()
            evaluateSingleExpression(editor, delete)
        } else {
            InfoPrompt("Cannot execute multiple statements that are not in the same block")
                .showDialog(context)
        }
    }

    private fun evaluateSingleExpression(editor: Any, delete: Boolean) {
        when {
            rootEditor is CodeBlockEditor && editor == rootEditor -> {
                evaluate(rootEditor.statements.result.now, anchorEditor = rootEditor) {
                    if (delete) {
                        rootEditor.variables.clear()
                        rootEditor.statements.clear()
                        addNewStatementAndSelect(rootEditor.statements)
                    }
                }
            }
            editor is ScExprEditor<*> -> {
                evaluate(listOf(editor.result.now), anchorEditor = editor) {
                    if (delete) {
                        val expander = editor as? ScExprExpander ?: editor.expander as? ScExprExpander
                        if (expander == null) {
                            InfoPrompt("Unable to delete expression").showDialog(context)
                            return@evaluate
                        }
                        if (expander.isExpanded.now) expander.reset()
                        else expander.setText("")
                        context[EditorControlGroup].getViewOf(editor).select()
                    }
                }
            }
            editor is ScExprListEditor -> {
                evaluate(editor.result.now, anchorEditor = editor.editors.now.last()) {
                    if (delete) {
                        editor.clear()
                        addNewStatementAndSelect(editor)
                    }
                }
            }
        }
    }

    private fun addNewStatementAndSelect(listEditor: ScExprListEditor) {
        val newEditor = ScExprExpander().defaultState()
        listEditor.addLast(newEditor)
        context[EditorControlGroup].getViewOf(newEditor).select()
    }

    private fun evaluateConsecutiveStatements(
        selectedEditors: List<ScExprEditor<*>>,
        singleParent: ScExprListEditor,
        delete: Boolean,
    ) {
        val selectedChildren = selectedEditors
            .mapNotNull { ed -> ed as? ScExprExpander ?: ed.expander as? ScExprExpander }
            .sortedBy { ed -> singleParent.editors.now.indexOf(ed) }
        evaluate(selectedChildren.map { ed -> ed.result.now }, anchorEditor = selectedChildren.last()) {
            if (delete) {
                val lastIndex = singleParent.editors.now.indexOf(selectedChildren.last())
                for (ed in selectedChildren) {
                    singleParent.remove(ed)
                }
                val newExpander = singleParent.addAt(lastIndex) ?: return@evaluate
                context[EditorControlGroup].getViewOf(newExpander).select()
            }
        }
    }

    private fun evaluate(statements: List<ScExpr>, anchorEditor: Editor<*>, onSuccess: () -> Unit) {
        val anchorNode = context[EditorControlGroup].getViewOf(anchorEditor)
        val code = writeCode {
            if (rootEditor is CodeBlockEditor) {
                val variables = rootEditor.variables.result.now
                if (variables.isNotEmpty()) {
                    append("var ")
                    append(variables.joinToString(", ") { id -> id.text })
                    appendLine(";")
                }
            }
            for (statement in statements) {
                statement.code(writer, context)
                appendLine("; ")
            }
        }
        resultWaiter.submit {
            try {
                val result = context[SuperColliderClient].eval(code).get()
                showResult(result, anchorNode, error = false)
                Platform.runLater(onSuccess)
            } catch (ex: ExecutionException) {
                val message = when (val cause = ex.cause) {
                    is SuperColliderException -> cause.errorMessage
                    is Exception -> cause.message ?: "Unknown Error"
                    else -> "Unknown Error"
                }
                showResult(message, anchorNode, error = true)
            } catch (e: Exception) {
                showResult("Error: ${e.message}", anchorNode, error = true)
                Logger.error("Error while evaluating code snippet", e)
            }
        }
    }

    private fun showResult(result: String, anchorNode: Region, error: Boolean) {
        Platform.runLater {
            val copyButton = copyResultAction.withContext(result).makeButton("medium-icon-button")
            val resultText = Text(limitString(result, 100))
            resultText.fill = if (error) Color.RED else Color.WHITE
            val layout = HBox(HBox(resultText).centerChildren(), VBox(infiniteSpace(), copyButton))
                .styleClass("result-pane")
            layout.border = createBorder(Color.GRAY, 2.0)
            layout.registerShortcuts(listOf(copyResultAction.withContext(result)))
            val popup = SubWindow(layout, "Result", SubWindow.Type.Popup)
            popup.scene.fill = Color.BLACK
            popup.scene.initHextantScene(context)
            val popupHeight = resultText.prefHeight(-1.0) + 30.0
            val offset = Point2D(anchorNode.width + 10.0, anchorNode.height - popupHeight)
            popup.show(anchorNode, offset)
        }
    }

    private fun limitString(input: String, maxLength: Int): String =
        if (input.length > maxLength) input.take(maxLength) + "..."
        else input

    companion object {
        private val resultWaiter = Executors.newCachedThreadPool()

        private val copyResultAction = action<String>("Copy result") {
            shortcut("Ctrl+C")
            icon(MaterialDesignC.CONTENT_COPY)
            executes { result ->
                val content = mapOf(DataFormat.PLAIN_TEXT to result)
                Clipboard.getSystemClipboard().setContent(content)
            }
        }

        private val evaluateSelectedCodeAction = action<CodePane>("Execute selected code") {
            icon(MaterialDesignS.SEND)
            shortcut("Ctrl+Shift?+Enter")
            executes { pane, ev ->
                pane.executeSelectedCode(delete = ev.isShiftDown())
            }
        }

        private val actions = collectActions<CodePane> {
            addAll(UndoRedoActions) { pane -> pane.context[UndoManager] }
            add(evaluateSelectedCodeAction)
        }
    }
}