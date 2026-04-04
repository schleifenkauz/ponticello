package ponticello.ui.controls

import fxutils.actions.button
import fxutils.prompt.PromptPlacement
import javafx.scene.Node
import org.kordamp.ikonli.materialdesign2.MaterialDesignL
import ponticello.model.instr.ParameterizedObject
import ponticello.model.score.controls.ParameterControl
import ponticello.model.score.controls.ParameterControlList
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
        namedControl: ParameterControlList.NamedParameterControl,
        control: TriggerControl,
        view: ScoreObjectView?
    ): Node = MaterialDesignL.LIGHTNING_BOLT.button("Trigger", "medium-icon-button") {
        control.trigger.fire()
    }

    override fun toString(): String = "Trig"
}