package ponticello.ui.score

import fxutils.actions.ContextualizedAction
import fxutils.actions.collectActions
import fxutils.actions.isShiftDown
import fxutils.setupDropArea
import fxutils.styleClass
import javafx.geometry.Point2D
import javafx.scene.Node
import javafx.scene.input.DataFormat
import javafx.scene.input.DragEvent
import javafx.scene.input.Dragboard
import org.kordamp.ikonli.codicons.Codicons
import org.kordamp.ikonli.material2.Material2MZ
import ponticello.impl.json
import ponticello.model.Settings
import ponticello.model.obj.ParameterizedObject
import ponticello.model.score.ParameterControlList
import ponticello.model.score.ParameterControlList.NamedParameterControl
import ponticello.model.score.controls.ParameterControl
import ponticello.sc.ControlSpec
import ponticello.ui.controls.ControlAssignmentEditor
import ponticello.ui.controls.ControlSpecPrompt
import ponticello.ui.launcher.PonticelloApp.Companion.primaryStage
import ponticello.ui.registry.ObjectBox
import ponticello.ui.registry.ObjectListView.DisplayMode
import ponticello.ui.registry.SearchableParameterDefListView
import ponticello.ui.registry.SearchableToolPane
import reaktive.value.binding.impl.notNull
import reaktive.value.now

class ParameterControlsPane(
    private val obj: ParameterizedObject, title: String,
    private val view: ScoreObjectView? = null,
) : SearchableToolPane<NamedParameterControl>(), ParameterControlList.Listener {
    private val editors = mutableMapOf<NamedParameterControl, ControlAssignmentEditor>()

    init {
        obj.controls.addListener(this)
        setup(title, obj.controls) { headerActions.withContext(this) }
        styleClass("parameter-controls")
        listView.itemsScrollPane.setupDropArea(::canDrop, ::drop)
    }

    private fun canDrop(db: Dragboard): Boolean = db.hasContent(NamedParameterControl.DATA_FORMAT)

    private fun drop(ev: DragEvent) {
        val db = ev.dragboard
        when {
            db.hasContent(NamedParameterControl.DATA_FORMAT) -> {
                val jsonString = db.getContent(NamedParameterControl.DATA_FORMAT) as String
                val namedControl = json.decodeFromString(NamedParameterControl.serializer(), jsonString)
                obj.controls.duplicateControl(namedControl)
            }
        }
    }

    override val enableReordering: Boolean
        get() = true

    override val supportedModes: Set<DisplayMode>
        get() = setOf(DisplayMode.Inline)

    override fun reassignedControl(
        parameter: NamedParameterControl,
        oldControl: ParameterControl,
        newControl: ParameterControl,
    ) {
        val editor = editors.getValue(parameter)
        editor.setControl(newControl)
    }

    override fun changedSpec(parameter: NamedParameterControl, oldSpec: ControlSpec?, newSpec: ControlSpec?) {
        val editor = editors[parameter] ?: return
        editor.setControl(parameter.now) //TODO can this be done in a better way?
    }

    override fun getItemContent(obj: NamedParameterControl): List<Node> {
        val editor = editors.getOrPut(obj) { ControlAssignmentEditor(obj, view) }
        editor.setControl(obj.now)
        return listOf(editor)
    }

    override fun dataFormat(obj: NamedParameterControl): DataFormat = NamedParameterControl.DATA_FORMAT

    override fun configureDragboard(obj: NamedParameterControl, dragboard: Dragboard) {
        val copy = obj.copy()
        if (copy.customSpec() == null) {
            val defaultSpec = obj.parentObject.def.getSpec(copy.name.now)?.now
            copy.setCustomSpec(defaultSpec)
        }
        val jsonString = json.encodeToString(NamedParameterControl.serializer(), copy)
        dragboard.setContent(mapOf(NamedParameterControl.DATA_FORMAT to jsonString))
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
                enableWhen { box -> box.obj.spec.notNull() }
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
            val synthParameters = obj.def.allParameters()
            val unassignedParameters = (synthParameters + defaultParameters)
                .filter { param -> param.name.now !in obj.controls.controlMap }
                .filter { param -> !(param in defaultParameters && synthParameters.any { p -> p.name.now == param.name.now }) }
            val option = SearchableParameterDefListView(unassignedParameters, "Add parameter", obj)
                .showPopup(anchor, context[primaryStage]) ?: return
            val parameter = option.name.now
            val customSpec = option.spec.now.takeIf { !obj.def.hasParameter(parameter) }
            val control = option.defaultControl()
            obj.controls.addControl(parameter, control, customSpec)
        }
    }
}