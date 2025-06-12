package ponticello.ui.registry

import fxutils.actions.collectActions
import fxutils.prompt.PredicateTextPrompt
import fxutils.prompt.SimpleSearchableListView
import javafx.scene.Parent
import org.kordamp.ikonli.material2.Material2MZ
import ponticello.model.obj.ParameterDefObject
import ponticello.sc.Identifier
import ponticello.sc.ParameterType
import ponticello.sc.defaultControlSpec
import ponticello.ui.dock.ToolPane
import reaktive.value.now

class ParameterDefsPane(private val parameters: ParameterDefList, override val title: String) : ToolPane() {
    private val config = ParameterListConfig()

    private val objectBoxList = ObjectListView(parameters, config)

    override val content: Parent
        get() = objectBoxList

    private fun addParameter() {
        val name = PredicateTextPrompt("Parameter name", initialText = "", check = { txt ->
            Identifier.isValid(txt) && parameters.none { p -> p.name.now == txt }
        }).showDialog(header) ?: return
        val type = SimpleSearchableListView(ParameterType.regularTypes, "Parameter type")
            .showPopup(objectBoxList, initialOption = ParameterType.Numerical) ?: return
        val spec = type.defaultControlSpec()
        val param = ParameterDefObject(name, spec)
        parameters.add(param)
    }

    companion object {
        private val actions = collectActions<ParameterDefsPane> {
            addAction("Add parameter") {
                icon(Material2MZ.PLUS)
                shortcut("Ctrl+PLUS")
                executes { pane -> pane.addParameter() }
            }
        }
    }
}