package ponticello.ui.registry

import fxutils.actions.collectActions
import fxutils.prompt.PredicateTextPrompt
import fxutils.prompt.SimpleSearchableListView
import hextant.context.Context
import org.kordamp.ikonli.material2.Material2MZ
import ponticello.model.obj.ParameterDefObject
import ponticello.sc.Identifier
import ponticello.sc.ParameterType
import ponticello.sc.defaultControlSpec
import reaktive.value.now

class ParameterDefsPane(private val parameters: ParameterDefList, context: Context, title: String) : ToolPane() {
    private val config = ParameterListConfig(context)

    private val objectBoxList = ObjectListView(parameters, config)

    init {
        setup(content = objectBoxList, title = title, actions = actions.withContext(this))
    }

    private fun addParameter() {
        val name = PredicateTextPrompt("Parameter name", initialText = "", check = { txt ->
            Identifier.isValid(txt) && parameters.none { p -> p.name.now == txt }
        }).showDialog(header ?: this) ?: return
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