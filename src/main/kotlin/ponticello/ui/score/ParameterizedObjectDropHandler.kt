package ponticello.ui.score

import fxutils.drag.ConfiguredDropHandler
import fxutils.prompt.SimpleSelectorPrompt
import fxutils.prompt.nextToTarget
import fxutils.solidBorder
import hextant.serial.EditorRoot
import javafx.scene.Node
import javafx.scene.input.DragEvent
import javafx.scene.input.TransferMode
import javafx.scene.layout.Region
import javafx.scene.paint.Color
import ponticello.impl.json
import ponticello.model.code.GlobalPatternObject
import ponticello.model.code.GlobalPatternRegistry
import ponticello.model.instr.BusObject
import ponticello.model.instr.ParameterizedObject
import ponticello.model.score.controls.BufferControl
import ponticello.model.score.controls.BusControl
import ponticello.model.score.controls.ExprControl
import ponticello.model.score.controls.ParameterControlList.NamedParameterControl
import ponticello.model.server.BufferObject
import ponticello.model.server.BufferRegistry
import ponticello.model.server.BusRegistry
import ponticello.sc.ControlSpec
import ponticello.sc.editor.GlobalPatternSelector
import ponticello.sc.editor.MessageSendEditor
import ponticello.sc.editor.ScExprExpander
import ponticello.ui.score.ParameterControlsPane.Companion.SERIALIZED_CONTROL_FORMAT
import ponticello.ui.score.ScoreObjectView.Companion.BORDER_RADIUS
import reaktive.value.now

class ParameterizedObjectDropHandler(
    private val obj: ParameterizedObject, private val view: Region,
) : ConfiguredDropHandler(json) {
    private val context get() = obj.context
    private var borderBefore: javafx.scene.layout.Border? = null

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
        handleTypedFormat(BusObject.DATA_FORMAT, TransferMode.LINK) { event, ref ->
            val bus = ref.resolve(context[BusRegistry]) ?: return@handleTypedFormat false
            droppedBus(bus, event)
            true
        }
        handleTypedFormat(BufferObject.DATA_FORMAT, TransferMode.LINK) { event, ref ->
            val buffer = ref.resolve(context[BufferRegistry]) ?: return@handleTypedFormat false
            droppedBuffer(buffer, event)
            true
        }
        handleTypedFormat(GlobalPatternObject.DATA_FORMAT, TransferMode.LINK) { event, ref ->
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
        obj.getInstrument().parameters
            .filter { p -> predicate(p.spec.now) }
            .mapTo(controlOptions) { p -> p.name.now }

        return when (controlOptions.size) {
            0 -> null
            1 -> controlOptions.single()
            else -> SimpleSelectorPrompt(controlOptions.toList(), "Select linked parameter")
                .showPopup(ev.nextToTarget())
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