package xenakis.ui.controls

import fxutils.actions.ContextualizedAction
import fxutils.actions.collectActions
import fxutils.actions.registerShortcuts
import javafx.scene.Node
import org.kordamp.ikonli.codicons.Codicons
import reaktive.value.binding.map
import xenakis.model.score.ParameterControl
import xenakis.model.score.ParameterControls
import xenakis.model.score.ParameterControls.NamedParameterControl
import xenakis.sc.ControlSpec
import xenakis.sc.NumericalControlSpec
import xenakis.ui.registry.NamedObjectListView
import xenakis.ui.registry.ObjectBoxConfig

class ParameterControlListConfig() : ObjectBoxConfig<NamedParameterControl>, ParameterControls.Listener {
    private val editors = mutableMapOf<NamedParameterControl, ControlAssignmentEditor>()

    override val enableReordering: Boolean
        get() = true

    override fun reassignedControl(
        namedControl: NamedParameterControl,
        oldControl: ParameterControl,
        control: ParameterControl
    ) {
        val editor = editors.getValue(namedControl)
        editor.setControl(control)
    }

    override fun changedSpec(control: NamedParameterControl, oldSpec: ControlSpec?, newSpec: ControlSpec?) {
        val editor = editors.getValue(control)
        editor.setControl(control.now) //TODO can this be done in a better way?
    }

    override fun getContent(obj: NamedParameterControl): List<Node> {
        val editor = editors.getOrPut(obj) { ControlAssignmentEditor(obj) }
        editor.setControl(obj.now)
        return listOf(editor)
    }

    override fun getActions(obj: NamedParameterControl): List<ContextualizedAction> = actions.withContext(obj)

    companion object {
        private val actions = collectActions<NamedParameterControl> {
            addAction("Edit spec") {
                shortcut("Ctrl+P")
                applicableIf { control -> control.spec.map { s -> s is NumericalControlSpec } }
                //editor.obj.def.getParameter(editor.parameter)!!.spec.map { s -> s is NumericalControlSpec }
                icon(Codicons.SYMBOL_PROPERTY)
                executes { control: NamedParameterControl, ev ->
                    ControlSpecPrompt(control).showDialog(ev)
                }
            }
        }

        fun makeControlListView(controls: ParameterControls): NamedObjectListView<NamedParameterControl> {
            val config = ParameterControlListConfig()
            val listView = NamedObjectListView(controls, config)
            controls.addListener(config)
            listView.userData = config
            listView.registerShortcuts(listView.actions)
            return listView
        }
    }
}