package ponticello.ui.misc

import bundles.set
import fxutils.actions.registerShortcuts
import fxutils.pad
import fxutils.registerShortcuts
import fxutils.undo.UndoManager
import hextant.context.Context
import hextant.context.SelectionDistributor
import hextant.context.extend
import hextant.core.editor.defaultState
import hextant.fx.initHextantScene
import hextant.serial.EditorRoot
import javafx.application.Platform
import javafx.scene.layout.StackPane
import javafx.scene.paint.Color
import javafx.stage.Popup
import ponticello.model.obj.project
import ponticello.sc.EmptyExpr
import ponticello.sc.client.SuperColliderClient
import ponticello.sc.client.eval
import ponticello.sc.code
import ponticello.sc.editor.ScExprExpander
import ponticello.sc.substitute
import ponticello.sc.unboundVariables
import ponticello.ui.actions.UndoRedoActions
import ponticello.ui.impl.sceneFill
import reaktive.value.now

class CodePopup private constructor(private val context: Context) : Popup() {
    private val pane = StackPane().pad(5.0)

    //The history of evaluated expressions in the order from last to first.
    private val history = mutableListOf<EditorRoot<ScExprExpander>>()

    private var currentHistoryIdx: Int = 0
        set(value) {
            field = value.coerceIn(history.indices)
            var root = history[field]
            if (currentHistoryIdx != 0) root = root.clone(context)
            pane.children.setAll(root.control)
            root.control.receiveFocus()
        }

    init {
        sceneFill(Color.BLACK)
        content.add(pane)
        scene.initHextantScene(context)
        scene.registerShortcuts {
            on("Ctrl+Enter") { evaluate() }
            on("Ctrl+UP") { currentHistoryIdx += 1 }
            on("Ctrl+DOWN") { currentHistoryIdx -= 1 }
        }
        clear()
    }

    private fun clear() {
        if (currentHistoryIdx != 0) {
            if (history.first().editor.result.now !is EmptyExpr) {
                history.first().editor.reset()
            }
            currentHistoryIdx = 0
            return
        }
        val editor = ScExprExpander().defaultState()
        val undoManager = UndoManager.newInstance()
        val ctx = context.extend {
            val selector = SelectionDistributor.newInstance()
            set(SelectionDistributor, selector)
            set(UndoManager, undoManager)
        }
        val root = EditorRoot.initialize(editor, ctx)
        root.control.registerShortcuts(UndoRedoActions.withContext(undoManager))
        pane.children.setAll(root.control)

        history.add(0, root)
        currentHistoryIdx = 0
    }

    private fun evaluate() {
        var expr = history[currentHistoryIdx].editor.result.now
        if (expr is EmptyExpr || !expr.isValid) return
        val unboundVariables = expr.unboundVariables()
        if (unboundVariables.isNotEmpty()) {
            val assignment = VariableAssignmentPrompt(unboundVariables, context)
                .showDialog(pane) ?: return
            expr = expr.substitute(assignment.mapValues { (_, v) -> { v } })
        }
        val code = expr.code(context)
        context[SuperColliderClient].eval(code)
            .handleAsync { result, exception ->
                val result =
                    if (exception != null) exception.message ?: "Unknown Error"
                    else result
                Platform.runLater {
                    ResultPopup(context, result, error = exception != null).show()
                    clear()
                    hide()
                }
            }
    }

    companion object {
        private val instances = mutableMapOf<String, CodePopup>()

        fun get(context: Context): CodePopup = instances.getOrPut(context.project.name) { CodePopup(context) }
    }
}