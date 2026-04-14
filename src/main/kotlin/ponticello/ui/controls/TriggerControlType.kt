package ponticello.ui.controls

import fxutils.actions.button
import fxutils.drag.putSerializableContent
import fxutils.prompt.PromptPlacement
import javafx.scene.Node
import javafx.scene.input.TransferMode
import org.kordamp.ikonli.materialdesign2.MaterialDesignL
import ponticello.impl.json
import ponticello.model.instr.ParameterizedObject
import ponticello.model.live.ItemTarget
import ponticello.model.registry.reference
import ponticello.model.score.controls.NamedParameterControl
import ponticello.model.score.controls.ParameterControl
import ponticello.model.score.controls.TriggerControl
import ponticello.sc.ControlSpec
import ponticello.sc.NumericalControlSpec
import ponticello.ui.score.ScoreObjectView

object TriggerControlType : ControlType<TriggerControl>() {
    override fun applicableOn(obj: ParameterizedObject, spec: ControlSpec): Boolean = when (spec) {
        is NumericalControlSpec -> true
        else -> false
    }

    override fun createInitialControl(
        obj: ParameterizedObject, spec: ControlSpec?,
        oldControl: ParameterControl?, parameterName: String,
        promptPlacement: PromptPlacement?
    ): TriggerControl = TriggerControl()

    override fun createDetailInput(
        namedControl: NamedParameterControl,
        control: TriggerControl,
        view: ScoreObjectView?
    ): Node {
        val button = MaterialDesignL.LIGHTNING_BOLT.button("Trigger", "medium-icon-button")
        button.setOnAction { ev ->
            control.trigger.fire()
            ev.consume()
        }
        button.setOnDragDetected { ev ->
            val dragboard = button.startDragAndDrop(TransferMode.LINK)
            val associatedObject = namedControl.parentObject.makeReference() ?: return@setOnDragDetected
            val trigger = ItemTarget.Trigger(associatedObject, namedControl.reference())
            dragboard.putSerializableContent(ItemTarget.DATA_FORMAT, trigger, json)
            dragboard.putSerializableContent(TriggerControl.DATA_FORMAT, trigger, json)
            dragboard.dragView = button.snapshot(null, null)
            ev.consume()
        }
        return button
    }

    override fun toString(): String = "Trig"
}