package xenakis.ui.registry

import fxutils.actions.collectActions
import fxutils.actions.isShiftDown
import fxutils.actions.registerShortcuts
import fxutils.letContentFillViewPort
import hextant.context.Context
import hextant.serial.EditorRoot
import javafx.scene.control.ScrollPane
import javafx.scene.layout.VBox
import org.kordamp.ikonli.material2.Material2MZ
import reaktive.value.ReactiveString
import xenakis.model.Settings
import xenakis.sc.editor.CodeBlockEditor

class ParameterizedObjectDefPane(
    private val context: Context,
    title: ReactiveString,
    private val parameters: ParameterDefList,
    code: EditorRoot<CodeBlockEditor>,
    private val update: () -> Unit
) : ToolPane() {
    private val config = ParameterListConfig(context)
    private val parametersList = NamedObjectListView(parameters, config)

    init {
        val content = ScrollPane(VBox(parametersList, code.control)).letContentFillViewPort()
        setup(title, content, actions = actions.withContext(this))
        parametersList.registerShortcuts(parametersList.actions)
    }

    private fun addParameter() {
        val defaultParameters = context[Settings].defaultParametersDefs
        val listView = SearchableParameterDefListView(defaultParameters, "New parameter")
        listView.showPopup(header) { newParam ->
            parameters.add(newParam)
            val idx = parameters.indices.last
            parametersList.select(idx)
        }
    }

    companion object {
        private val actions = collectActions<ParameterizedObjectDefPane> {
            addAction("Add parameter") {
                icon(Material2MZ.PLUS)
                shortcuts("Ctrl+P")
                executes { pane -> pane.addParameter() }
            }
            addAction("Update SuperCollider object") {
                icon(Material2MZ.SYNC)
                shortcuts("Ctrl+Shift?+U")
                executes { pane, ev ->
                    pane.update()
                    if (ev.isShiftDown()) pane.scene.window.hide()
                }
            }
        }
    }
}