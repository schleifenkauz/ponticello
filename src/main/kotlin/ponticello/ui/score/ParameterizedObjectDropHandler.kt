package ponticello.ui.score

import fxutils.drag.ConfiguredDropHandler
import fxutils.prompt.SimpleSearchableListView
import fxutils.solidBorder
import hextant.serial.EditorRoot
import javafx.scene.Node
import javafx.scene.input.DragEvent
import javafx.scene.layout.Region
import javafx.scene.paint.Color
import ponticello.impl.json
import ponticello.model.obj.BufferObject
import ponticello.model.obj.BusObject
import ponticello.model.obj.GlobalPatternObject
import ponticello.model.obj.ParameterizedObject
import ponticello.model.registry.BufferRegistry
import ponticello.model.registry.BusRegistry
import ponticello.model.registry.GlobalPatternRegistry
import ponticello.model.score.ParameterControlList.NamedParameterControl
import ponticello.model.score.controls.BufferControl
import ponticello.model.score.controls.BusControl
import ponticello.model.score.controls.ExprControl
import ponticello.sc.ControlSpec
import ponticello.sc.editor.GlobalPatternSelector
import ponticello.sc.editor.MessageSendEditor
import ponticello.sc.editor.ScExprExpander
import ponticello.ui.score.ParameterControlsPane.Companion.SERIALIZED_CONTROL_FORMAT
import ponticello.ui.score.ScoreObjectView.Companion.BORDER_RADIUS
import reaktive.value.now

class ParameterizedObjectDropHandler(
    private val obj: ParameterizedObject, private val view: Region,
) : ConfiguredDropHandler() {
    private val context get() = obj.context
    private var borderBefore: javafx.scene.layout.Border? = null

    override fun canDrop(event: DragEvent): Boolean {
        val db = event.dragboard
        return when {
            db.hasContent(NamedParameterControl.DATA_FORMAT) -> true
            db.hasContent(BusObject.DATA_FORMAT) -> true
            db.hasContent(BufferObject.DATA_FORMAT) -> true
            else -> false
        }
    }

    init {
        handleFormat(SERIALIZED_CONTROL_FORMAT) { _, db ->
            val jsonString = db.getContent(SERIALIZED_CONTROL_FORMAT) as String
            val control = json.decodeFromString<NamedParameterControl>(jsonString)
            obj.controls.duplicateControl(control)
            true
        }
        handleTypedFormat(NamedParameterControl.DATA_FORMAT) { _, namedControl ->
            obj.controls.duplicateControl(namedControl)
            true
        }
        handleTypedFormat(BusObject.DATA_FORMAT) { event, ref ->
            val bus = ref.resolve(context[BusRegistry]) ?: return@handleTypedFormat false
            droppedBus(bus, event)
            true
        }
        handleTypedFormat(BufferObject.DATA_FORMAT) { event, ref ->
            val buffer = ref.resolve(context[BufferRegistry]) ?: return@handleTypedFormat false
            droppedBuffer(buffer, event)
            true
        }
        handleTypedFormat(GlobalPatternObject.DATA_FORMAT) { event, ref ->
            val pattern = ref.resolve(context[GlobalPatternRegistry]) ?: return@handleTypedFormat false
            val expr = ScExprExpander(
                MessageSendEditor(
                    ScExprExpander(
                        GlobalPatternSelector().also { it.selectInitial(pattern) }
                    )
                )
            )
            val control = ExprControl(EditorRoot(expr))
            val controlName = getAssignedName(event) { _ -> true } ?: return@handleTypedFormat false
            obj.controls.assignControl(controlName, control)
            true
        }
    }

    private fun droppedBus(bus: BusObject, ev: DragEvent) {
        val assignedName = getAssignedName(ev) { spec -> bus.matches(spec) } ?: return
        val control = BusControl.create(bus)
        obj.controls.assignControl(assignedName, control)
    }

    private fun droppedBuffer(buffer: BufferObject, ev: DragEvent) {
        val assignedName = getAssignedName(ev) { spec -> buffer.matches(spec) } ?: return
        val control = BufferControl.create(buffer)
        obj.controls.assignControl(assignedName, control)
    }

    private fun getAssignedName(ev: DragEvent, predicate: (ControlSpec?) -> Boolean): String? {
        val controlOptions = mutableSetOf<String>()
        obj.controls
            .filter { ctrl -> predicate(ctrl.spec.now) }
            .mapTo(controlOptions) { ctrl -> ctrl.name.now }
        obj.def.parameters
            .filter { p -> predicate(p.spec.now) }
            .mapTo(controlOptions) { p -> p.name.now }

        return when (controlOptions.size) {
            0 -> null
            1 -> controlOptions.single()
            else -> SimpleSearchableListView(controlOptions.toList(), "Select linked parameter").showPopup(ev)
        }
    }

    override fun Node.updateDropPossible(possible: Boolean) {
        if (possible) {
            borderBefore = view.border
            view.border = solidBorder(Color.GREEN, 3.0, BORDER_RADIUS)
        } else {
            view.border = borderBefore
        }
    }
}