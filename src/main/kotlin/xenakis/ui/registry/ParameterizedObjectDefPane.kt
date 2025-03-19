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
import reaktive.list.MutableReactiveList
import reaktive.value.ReactiveString
import xenakis.model.Settings
import xenakis.model.obj.ParameterDefObject
import xenakis.sc.editor.CodeBlockEditor

class ParameterizedObjectDefPane(
    private val context: Context,
    title: ReactiveString,
    private val parameters: MutableReactiveList<ParameterDefObject>,
    code: EditorRoot<CodeBlockEditor>,
    private val update: () -> Unit
) : ToolPane() {
    private val config = ParameterListSource(context, parameters)
    private val parametersList = ObjectBoxList(config)

    init {
        config.syncWithBoxList(parametersList)
        val content = ScrollPane(VBox(parametersList, code.control)).letContentFillViewPort()
        setup(title, content, actions = actions.withContext(this))
        parametersList.registerShortcuts(parametersList.actions)
    }

    fun addParameter() {
        val defaultParameters = context[Settings].defaultParametersDefs.now
        val listView = SearchableParameterDefListView(defaultParameters, "New parameter")
        listView.showPopup(header) { newParam ->
            parameters.now.add(newParam)
            val idx = parameters.now.indices.last
            parametersList.select(idx)
        }
    }

    companion object {
        private val actions = collectActions<ParameterizedObjectDefPane> {
            addAction("Add parameter") {
                icon(Material2MZ.PLUS)
                shortcuts("Ctrl+PLUS")
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