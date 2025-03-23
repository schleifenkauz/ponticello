package xenakis.ui.score

import fxutils.actions.ContextualizedAction
import fxutils.actions.collectActions
import fxutils.actions.isShiftDown
import fxutils.actions.registerShortcuts
import javafx.scene.Node
import org.kordamp.ikonli.codicons.Codicons
import reaktive.value.binding.map
import xenakis.model.obj.ParameterizedObject
import xenakis.model.score.ParameterControl
import xenakis.model.score.ParameterControlList
import xenakis.model.score.ParameterControlList.NamedParameterControl
import xenakis.sc.ControlSpec
import xenakis.sc.NumericalControlSpec
import xenakis.ui.controls.ControlAssignmentEditor
import xenakis.ui.controls.ControlSpecPrompt
import xenakis.ui.registry.NamedObjectListView.ContentDisplay
import xenakis.ui.registry.SearchableToolPane
import xenakis.ui.score.ParameterizedScoreObjectView.Companion.addNewControl

class ParameterControlsPane(
    private val obj: ParameterizedObject, title: String,
) : SearchableToolPane<NamedParameterControl>(), ParameterControlList.Listener {
    private val editors = mutableMapOf<NamedParameterControl, ControlAssignmentEditor>()

    init {
        obj.controls.addListener(this)
        setup(title, obj.controls) { headerActions.withContext(this) }
        registerShortcuts(listView.actions)
    }

    override val enableReordering: Boolean
        get() = true

    override val supportedModes: Set<ContentDisplay>
        get() = setOf(ContentDisplay.Inline, ContentDisplay.DetailsPane)

    override fun reassignedControl(
        namedControl: NamedParameterControl,
        oldControl: ParameterControl,
        control: ParameterControl,
    ) {
        val editor = editors.getValue(namedControl)
        editor.setControl(control)
    }

    override fun changedSpec(control: NamedParameterControl, oldSpec: ControlSpec?, newSpec: ControlSpec?) {
        val editor = editors.getValue(control)
        editor.setControl(control.now) //TODO can this be done in a better way?
    }

    override fun getItemContent(obj: NamedParameterControl): List<Node> {
        val editor = editors.getOrPut(obj) { ControlAssignmentEditor(obj) }
        editor.setControl(obj.now)
        return listOf(editor)
    }

    override fun getActions(obj: NamedParameterControl): List<ContextualizedAction> = actions.withContext(obj)

    companion object {
        private val headerActions = collectActions<ParameterControlsPane> {
            addAction("Add parameter") {
                executes { p, ev ->
                    if (ev.isShiftDown()) {
                        p.obj.addControlsForAllObjectParameters()
                    } else {
                        addNewControl(p.obj, p.header)
                    }
                }
            }
        }

        private val actions = collectActions<NamedParameterControl> {
            addAction("Edit spec") {
                shortcut("Ctrl+K")
                applicableIf { control -> control.spec.map { s -> s is NumericalControlSpec } }
                icon(Codicons.SYMBOL_PROPERTY)
                executes { control: NamedParameterControl, ev ->
                    ControlSpecPrompt(control).showDialog(ev)
                }
            }
        }
    }
}