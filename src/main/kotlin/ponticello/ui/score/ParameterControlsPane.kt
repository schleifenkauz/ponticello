package ponticello.ui.score

import fxutils.actions.ContextualizedAction
import fxutils.actions.collectActions
import fxutils.actions.isShiftDown
import fxutils.styleClass
import javafx.event.Event
import javafx.geometry.Point2D
import javafx.scene.Node
import javafx.scene.input.DataFormat
import javafx.scene.input.DragEvent
import javafx.scene.input.Dragboard
import javafx.scene.input.TransferMode
import javafx.scene.input.TransferMode.COPY_OR_MOVE
import org.kordamp.ikonli.Ikon
import org.kordamp.ikonli.codicons.Codicons
import ponticello.impl.json
import ponticello.model.Settings
import ponticello.model.obj.ParameterizedObject
import ponticello.model.obj.withName
import ponticello.model.project.PonticelloProject
import ponticello.model.registry.ObjectList
import ponticello.model.score.ParameterControlList
import ponticello.model.score.ParameterControlList.NamedParameterControl
import ponticello.model.score.controls.ParameterControl
import ponticello.sc.ControlSpec
import ponticello.ui.controls.ControlAssignmentEditor
import ponticello.ui.controls.ControlSpecPrompt
import ponticello.ui.dock.SearchableToolPane
import ponticello.ui.dock.Side
import ponticello.ui.dock.ToolPane
import ponticello.ui.registry.ObjectBox
import ponticello.ui.registry.ObjectListView.DisplayMode
import ponticello.ui.registry.SearchableParameterDefListView
import reaktive.value.binding.impl.notNull
import reaktive.value.now

class ParameterControlsPane(
    private val obj: ParameterizedObject, private val view: ScoreObjectView? = null,
) : SearchableToolPane<NamedParameterControl>(obj.controls, scrollable = false), ParameterControlList.Listener {
    override val type: Type
        get() = ParameterControlsPane

    private val editors = mutableMapOf<NamedParameterControl, ControlAssignmentEditor>()

    override val title: String
        get() = "Parameter controls"

    override val supportedModes: Set<DisplayMode>
        get() = setOf(DisplayMode.Inline(collapsable = false))

    init {
        styleClass("parameter-controls")
        setup()
    }

    override fun afterSetup() {
        super.afterSetup()
        obj.controls.addListener(this)
    }

    override fun createNewObject(ev: Event?, list: ObjectList<NamedParameterControl>): NamedParameterControl? {
        if (ev.isShiftDown()) {
            obj.addControlsForAllObjectParameters()
            return null
        } else {
            return createNewControl(ev)
        }
    }

    private fun createNewControl(ev: Event?): NamedParameterControl? {
        val context = obj.context
        val defaultParameters = context[Settings].defaultParametersDefs
        val synthParameters = obj.def.allParameters()
        val unassignedParameters = (synthParameters + defaultParameters)
            .filter { param -> param.name.now !in obj.controls.controlMap }
            .filter { param -> !(param in defaultParameters && synthParameters.any { p -> p.name.now == param.name.now }) }
        val option = SearchableParameterDefListView(unassignedParameters, "Add parameter", obj)
            .showPopup(ev) ?: return null
        val parameter = option.name.now
        val customSpec = option.spec.now.takeIf { !obj.def.hasParameter(parameter) }
        val control = option.defaultControl()
        return NamedParameterControl(control, customSpec).withName(parameter)
    }

    override fun acceptedTransferModes(dragboard: Dragboard): Array<TransferMode> =
        if (dragboard.hasContent(dataFormat)) COPY_OR_MOVE
        else if (dragboard.hasContent(serializedControlFormat)) COPY_OR_MOVE
        else emptyArray()

    override fun getDroppedObject(ev: DragEvent): NamedParameterControl? = when {
        ev.gestureSource !in listView.getBoxes() && ev.dragboard.hasContent(serializedControlFormat) -> {
            val jsonString = ev.dragboard.getContent(serializedControlFormat) as String
            val obj = json.decodeFromString<NamedParameterControl>(jsonString)
            obj
        }

        else -> null
    }

    override fun dropObject(obj: NamedParameterControl, idx: Int, list: ObjectList<NamedParameterControl>) {
        if (list is ParameterControlList) {
            list.duplicateControl(obj, idx)
        } else {
            super.dropObject(obj, idx, list)
        }
    }

    override val dataFormat: DataFormat
        get() = NamedParameterControl.DATA_FORMAT

    override fun configureDragboard(obj: NamedParameterControl, dragboard: Dragboard) {
        val copy = obj.copy()
        if (copy.customSpec() == null) {
            val defaultSpec = obj.parentObject.def.getSpec(copy.name.now)?.now
            copy.setCustomSpec(defaultSpec)
        }
        val jsonString = json.encodeToString(NamedParameterControl.serializer(), obj)
        dragboard.setContent(mapOf(serializedControlFormat to jsonString))
    }

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

    override fun getHeaderContent(obj: NamedParameterControl): List<Node> {
        val editor = editors.getOrPut(obj) { ControlAssignmentEditor(obj, view) }
        editor.setControl(obj.now)
        return listOf(editor)
    }

    override val addSpaceBeforeActionBar: Boolean get() = false

    override fun getActions(box: ObjectBox<NamedParameterControl>): List<ContextualizedAction> =
        actions.withContext(box)

    companion object : Type(uid = -1, "ParameterControlsPane") {
        override val defaultSide: Side
            get() = Side.TOP
        override val icon: Ikon?
            get() = null

        override fun createToolPane(project: PonticelloProject): ToolPane {
            throw UnsupportedOperationException()
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

        private val serializedControlFormat = DataFormat("ponticello/parameter-control-reference")
    }
}