package ponticello.ui.score

import fxutils.actions.ContextualizedAction
import fxutils.actions.collectActions
import fxutils.actions.isShiftDown
import fxutils.neverSquishVertically
import fxutils.styleClass
import javafx.event.Event
import javafx.geometry.Point2D
import javafx.scene.Node
import javafx.scene.control.ScrollPane
import javafx.scene.input.DataFormat
import javafx.scene.input.DragEvent
import javafx.scene.input.Dragboard
import javafx.scene.input.TransferMode
import javafx.scene.input.TransferMode.COPY_OR_MOVE
import org.kordamp.ikonli.codicons.Codicons
import ponticello.impl.json
import ponticello.model.GlobalSettings
import ponticello.model.instr.ParameterizedObject
import ponticello.model.obj.withName
import ponticello.model.registry.ObjectList
import ponticello.model.registry.reference
import ponticello.model.score.controls.*
import ponticello.model.score.controls.ParameterControlList.NamedParameterControl
import ponticello.sc.ControlSpec
import ponticello.sc.NumericalControlSpec
import ponticello.sc.defaultControlType
import ponticello.ui.controls.ControlAssignmentEditor
import ponticello.ui.controls.ControlSpecPrompt
import ponticello.ui.dock.ListToolPane
import ponticello.ui.midi.MidiContext
import ponticello.ui.registry.ObjectBox
import ponticello.ui.registry.ObjectListView
import ponticello.ui.registry.ObjectListView.DisplayMode
import ponticello.ui.registry.ParameterDefSelectorPrompt
import reaktive.Reactive
import reaktive.value.ReactiveValue
import reaktive.value.binding.impl.notNull
import reaktive.value.now
import reaktive.value.reactiveValue

class ParameterControlsPane(
    val obj: ParameterizedObject, private val view: ScoreObjectView? = null,
    private val midiContext: MidiContext? = null,
) : ListToolPane<NamedParameterControl>(obj.controls, scrollable = false), ParameterControlList.Listener {
    private val editors = mutableMapOf<NamedParameterControl, ControlAssignmentEditor>()

    override val title: ReactiveValue<String>
        get() = reactiveValue("Parameter controls")

    override val supportedModes: Set<DisplayMode>
        get() = setOf(DisplayMode.Collapsable)

    override val addSpaceBeforeActionBar: Boolean get() = false

    override val canDuplicate: Boolean
        get() = true

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
        val defaultParameters = context[GlobalSettings].defaultParametersDefs
        val synthParameters = obj.getInstrument().allParameters()
        val unassignedParameters = (synthParameters + defaultParameters)
            .filter { param -> param.name.now !in obj.controls.controlMap }
            .filter { param -> !(param in defaultParameters && synthParameters.any { p -> p.name.now == param.name.now }) }
        val option = ParameterDefSelectorPrompt(unassignedParameters, "Add parameter", obj)
            .showPopup(ev) ?: return null
        val parameter = option.name.now
        val spec = option.spec.now
        val customSpec = spec.takeIf { !obj.getInstrument().hasParameter(parameter) }
        val type = spec.defaultControlType()
        val control =
            if (!type.supportsDialogInput()) option.defaultControl()
            else type.createInitialControl(obj, spec, oldControl = null, parameter, ev)
        return NamedParameterControl(control, customSpec).withName(parameter)
    }

    override fun acceptedTransferModes(dragboard: Dragboard): Array<TransferMode> =
        if (dragboard.hasContent(SERIALIZED_CONTROL_FORMAT)) COPY_OR_MOVE
        else if (dragboard.hasContent(dataFormat)) arrayOf(TransferMode.MOVE)
        else emptyArray()

    override fun getDroppedObjects(
        ev: DragEvent,
        targetView: ObjectListView<NamedParameterControl>,
    ): List<NamedParameterControl> = when {
        ev.gestureSource !in targetView.getBoxes() && ev.dragboard.hasContent(SERIALIZED_CONTROL_FORMAT) -> {
            val jsonString = ev.dragboard.getContent(SERIALIZED_CONTROL_FORMAT) as String
            listOf(json.decodeFromString<NamedParameterControl>(jsonString))
        }

        else -> super.getDroppedObjects(ev, targetView)
    }

    override fun dropObject(
        obj: NamedParameterControl, idx: Int,
        list: ObjectList<NamedParameterControl>, from: ObjectList<NamedParameterControl>?,
    ) {
        if (list is ParameterControlList) {
            list.duplicateControl(obj, idx)
        } else {
            super.dropObject(obj, idx, list, from)
        }
    }

    override val dataFormat: DataFormat
        get() = NamedParameterControl.DATA_FORMAT

    override fun configureDragboard(obj: NamedParameterControl, dragboard: Dragboard) {
        val copy = obj.copy()
        if (copy.customSpec() == null) {
            val defaultSpec = obj.parentObject.getInstrument().getSpec(copy.name.now)?.now
            copy.setCustomSpec(defaultSpec)
        }
        val jsonString = json.encodeToString(NamedParameterControl.serializer(), obj)
        dragboard.setContent(
            mapOf(
                NamedParameterControl.DATA_FORMAT to obj.reference(),
                SERIALIZED_CONTROL_FORMAT to jsonString
            )
        )
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
        editor.setControl(parameter.now)
    }

    override fun getHeaderContent(obj: NamedParameterControl): List<Node> {
        val editor = editors.getOrPut(obj) { ControlAssignmentEditor(obj, view, this) }
        editor.setControl(obj.now)
        return listOf(editor)
    }

    override fun contentUpdate(obj: NamedParameterControl): Reactive = obj.value()

    override fun getContent(obj: NamedParameterControl, box: ObjectBox<NamedParameterControl>) =
        when (val ctrl = obj.now) {
            is ExprControl -> ScrollPane(ctrl.expr.control).neverSquishVertically()
            is UGenControl -> ScrollPane(ctrl.expr.control).neverSquishVertically()
            else -> null
        }

    override fun getActions(box: ObjectBox<NamedParameterControl>): List<ContextualizedAction> =
        actions.withContext(box)

    override fun extraHeaderActions(): List<ContextualizedAction> =
        if (midiContext != null) listOf(MidiContext.toggleActiveAction.withContext(midiContext))
        else emptyList()

    companion object {
        private val actions = collectActions<ObjectBox<NamedParameterControl>> {
            addAction("Edit spec") {
                enableWhen { box -> box.obj.spec.notNull() }
                icon(Codicons.SYMBOL_PROPERTY)
                shortcut("Ctrl+P")
                executes { box ->
                    val control = box.obj
                    val initialSpec = control.spec.now ?: return@executes
                    ControlSpecPrompt.create(
                        control.name.now, control.parentObject, initialSpec
                    )?.showDialog(box, offset = Point2D(box.width, 0.0))
                }
            }
            addAction("Select control type") {
                shortcut("Ctrl+T")
                executes { box, ev ->
                    val pane = box.config as? ParameterControlsPane ?: return@executes
                    val editor = pane.editors[box.obj] ?: return@executes
                    editor.showOptionPopup(ev)
                }
            }
            addAction("Increase value") {
                //enableWhen { box -> box.obj.value().map { ctrl -> ctrl is ValueControl } and box.obj.spec }
                shortcut("PLUS")
                executes { box ->
                    val ctrl = box.obj.now as? ValueControl ?: return@executes
                    val spec = box.obj.spec.now as? NumericalControlSpec ?: return@executes
                    ctrl.value.now = (ctrl.value.now + spec.step.get()).coerceIn(spec.range)
                }
            }
            addAction("Decrease value") {
                //enableWhen { box -> box.obj.value().map { ctrl -> ctrl is ValueControl } and box.obj.spec }
                shortcut("MINUS")
                executes { box ->
                    val ctrl = box.obj.now as? ValueControl ?: return@executes
                    val spec = box.obj.spec.now as? NumericalControlSpec ?: return@executes
                    ctrl.value.now = (ctrl.value.now - spec.step.get()).coerceIn(spec.range)
                }
            }
        }

        val SERIALIZED_CONTROL_FORMAT = DataFormat("ponticello/parameter-control-serialized")
    }
}