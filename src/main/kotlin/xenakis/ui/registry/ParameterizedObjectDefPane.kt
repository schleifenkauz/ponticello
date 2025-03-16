package xenakis.ui.registry

import fxutils.actions.collectActions
import fxutils.actions.registerShortcuts
import hextant.context.Context
import hextant.serial.EditorRoot
import javafx.scene.Node
import javafx.scene.layout.VBox
import org.kordamp.ikonli.material2.Material2MZ
import reaktive.list.MutableReactiveList
import reaktive.value.ReactiveString
import xenakis.model.Settings
import xenakis.model.obj.ParameterDefObject
import xenakis.sc.editor.CodeBlockEditor

class ParameterizedObjectDefPane(
    private val context: Context,
    private val title: ReactiveString,
    private val parameters: MutableReactiveList<ParameterDefObject>,
    private val code: EditorRoot<CodeBlockEditor>
) : ToolPane() {
    private val config = ParameterListSource(context, parameters)
    private val parametersList = ObjectBoxList(config)

    init {
        config.syncWithBoxList(parametersList)
        registerShortcuts(actions.withContext(this))
    }

    override fun getTitle(): ReactiveString = title

    override fun getContent(): Node = VBox(parametersList, code.control)

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
        }
    }
}