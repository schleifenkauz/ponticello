package ponticello.ui.registry

import fxutils.actions.collectActions
import fxutils.prompt.PredicateTextPrompt
import fxutils.prompt.SimpleSearchableListView
import javafx.scene.Parent
import org.kordamp.ikonli.material2.Material2MZ
import ponticello.model.GlobalSettings
import ponticello.model.obj.ParameterDefObject
import ponticello.model.project.PonticelloProject
import ponticello.sc.Identifier
import ponticello.sc.ParameterType
import ponticello.sc.defaultControlSpec
import ponticello.ui.dock.Side
import ponticello.ui.dock.ToolPane
import reaktive.value.now

class ParameterDefsPane(private val parameters: ParameterDefList, override val title: String) : ToolPane() {
    private val config = ParameterListConfig()

    override val type: Type
        get() = ParameterDefsPane

    private val objectBoxList = ObjectListView(parameters, config, scrollable = true)

    override val content: Parent
        get() = objectBoxList

    init {
        setup()
    }

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

    companion object : Type(-1, "Parameters") {
        override val defaultSide: Side
            get() = Side.RIGHT

        override fun createToolPane(project: PonticelloProject): ToolPane =
            ParameterDefsPane(project.context[GlobalSettings].defaultParametersDefs, "Parameter definitions")

        private val actions = collectActions<ParameterDefsPane> {
            addAction("Add parameter") {
                icon(Material2MZ.PLUS)
                shortcut("Ctrl+PLUS")
                executes { pane -> pane.addParameter() }
            }
        }
    }
}