package xenakis.ui.score

import fxutils.actions.ContextualizedAction
import fxutils.actions.collectActions
import fxutils.actions.isShiftDown
import fxutils.actions.registerShortcuts
import javafx.geometry.Point2D
import javafx.scene.Node
import org.kordamp.ikonli.codicons.Codicons
import org.kordamp.ikonli.material2.Material2MZ
import reaktive.value.binding.map
import reaktive.value.now
import xenakis.model.Settings
import xenakis.model.obj.ParameterizedObject
import xenakis.model.score.ParameterControlList
import xenakis.model.score.ParameterControlList.NamedParameterControl
import xenakis.model.score.controls.ParameterControl
import xenakis.sc.ControlSpec
import xenakis.sc.GroupControlSpec
import xenakis.ui.controls.ControlAssignmentEditor
import xenakis.ui.controls.ControlSpecPrompt
import xenakis.ui.launcher.XenakisApp.Companion.primaryStage
import xenakis.ui.registry.NamedObjectListView.DisplayMode
import xenakis.ui.registry.ObjectBox
import xenakis.ui.registry.SearchableParameterDefListView
import xenakis.ui.registry.SearchableToolPane

class ParameterControlsPane(
    private val obj: ParameterizedObject, title: String,
    private val view: ScoreObjectView? = null,
) : SearchableToolPane<NamedParameterControl>(), ParameterControlList.Listener {
    private val editors = mutableMapOf<NamedParameterControl, ControlAssignmentEditor>()

    init {
        obj.controls.addListener(this)
        setup(title, obj.controls) { headerActions.withContext(this) }
        registerShortcuts(listView.actions)
    }

    override val enableReordering: Boolean
        get() = true

    override val supportedModes: Set<DisplayMode>
        get() = setOf(DisplayMode.Inline, DisplayMode.DetailsPane)

    override fun reassignedControl(
        namedControl: NamedParameterControl,
        oldControl: ParameterControl,
        control: ParameterControl,
    ) {
        val editor = editors.getValue(namedControl)
        editor.setControl(control)
    }

    override fun changedSpec(control: NamedParameterControl, oldSpec: ControlSpec?, newSpec: ControlSpec?) {
        val editor = editors[control] ?: return
        editor.setControl(control.now) //TODO can this be done in a better way?
    }

    override fun getItemContent(obj: NamedParameterControl): List<Node> {
        val editor = editors.getOrPut(obj) { ControlAssignmentEditor(obj, view) }
        editor.setControl(obj.now)
        return listOf(editor)
    }

    override fun getActions(box: ObjectBox<NamedParameterControl>): List<ContextualizedAction> =
        actions.withContext(box)

    companion object {
        private val headerActions = collectActions<ParameterControlsPane> {
            addAction("Add parameter") {
                icon(Material2MZ.PLUS)
                shortcut("Ctrl+PLUS")
                executes { p, ev ->
                    if (ev.isShiftDown()) {
                        p.obj.addControlsForAllObjectParameters()
                    } else {
                        addNewControl(p.obj, p.localToScreen(0.0, p.height))
                    }
                }
            }
        }

        private val actions = collectActions<ObjectBox<NamedParameterControl>> {
            addAction("Edit spec") {
                shortcut("Ctrl+K")
                applicableIf { box -> box.obj.spec.map { s -> s != null && s !is GroupControlSpec } }
                icon(Codicons.SYMBOL_PROPERTY)
                executes { box ->
                    val control = box.obj
                    val initialSpec = control.spec.now ?: return@executes
                    ControlSpecPrompt.create(
                        control.name.now, control.parentObject, initialSpec
                    )?.showDialog(box, offset = Point2D(box.width, 0.0))
                }
            }
        }

        private fun addNewControl(obj: ParameterizedObject, anchor: Point2D) {
            val context = obj.context
            val defaultParameters = context[Settings].defaultParametersDefs
            val synthParameters = obj.def.parameters
            val unassignedParameters = (synthParameters + defaultParameters)
                .filter { param -> param.name.now !in obj.controls.controlMap }
                .filter { param -> !(param in defaultParameters && synthParameters.any { p -> p.name.now == param.name.now }) }
            val option = SearchableParameterDefListView(
                unassignedParameters, "Add parameter", obj,
                context[primaryStage], anchor
            ).showPopup() ?: return
            val parameter = option.name.now
            val customSpec = option.spec.now.takeIf { !obj.def.hasParameter(parameter) }
            val control = option.defaultControl(context)
            obj.controls.addControl(parameter, control, customSpec)
        }
    }
}