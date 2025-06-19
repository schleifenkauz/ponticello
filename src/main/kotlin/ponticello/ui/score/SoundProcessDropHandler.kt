package ponticello.ui.score

import fxutils.drag.ConfiguredDropHandler
import fxutils.prompt.SimpleSearchableListView
import fxutils.solidBorder
import javafx.scene.Node
import javafx.scene.input.DragEvent
import javafx.scene.paint.Color
import ponticello.model.obj.BufferObject
import ponticello.model.obj.BusObject
import ponticello.model.registry.BufferRegistry
import ponticello.model.registry.BusRegistry
import ponticello.model.score.ParameterControlList.NamedParameterControl
import ponticello.model.score.controls.BufferControl
import ponticello.model.score.controls.BusControl
import ponticello.sc.ControlSpec
import ponticello.ui.score.ScoreObjectView.Companion.BORDER_RADIUS
import reaktive.value.now

class SoundProcessDropHandler(private val view: SoundProcessView): ConfiguredDropHandler() {
    private val obj get() = view.obj
    private val context get() = view.context
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