package ponticello.ui.controls

import fxutils.actions.ContextualizedAction
import fxutils.actions.collectActions
import fxutils.actions.detailsAction
import fxutils.controls.CheckBox
import fxutils.opacity
import fxutils.setFixedWidth
import fxutils.undo.UndoManager
import javafx.scene.Node
import javafx.scene.layout.Region
import ponticello.impl.asTime
import ponticello.model.obj.ParameterizedObject
import ponticello.model.score.ParameterControlList
import ponticello.model.score.ScoreObject
import ponticello.model.score.controls.EnvelopeControl
import ponticello.model.score.controls.ParameterControl
import ponticello.model.score.controls.getNumericalValue
import ponticello.sc.ControlSpec
import ponticello.sc.NumericalControlSpec
import ponticello.ui.impl.DEFAULT_SCENE_FILL
import ponticello.ui.impl.colorPicker
import ponticello.ui.score.ScoreObjectView
import reaktive.value.now
import reaktive.value.reactiveVariable

data object EnvelopeControlType : ControlType<EnvelopeControl>() {
    override fun applicableOn(obj: ParameterizedObject, spec: ControlSpec): Boolean =
        obj is ScoreObject && spec is NumericalControlSpec

    override fun createDetailInput(
        namedControl: ParameterControlList.NamedParameterControl, control: EnvelopeControl, view: ScoreObjectView?,
    ): Node {
        if (namedControl.spec.now !is NumericalControlSpec) return missingSpecOptionsBar(namedControl)
        val colorPicker = colorPicker(control.displayColor)
        colorPicker.setFixedWidth(30.0)
        return colorPicker
    }

    override fun createInitialControl(
        obj: ParameterizedObject,
        spec: ControlSpec?,
        oldControl: ParameterControl,
        namedControl: ParameterControlList.NamedParameterControl,
        anchorNode: Region,
    ): EnvelopeControl {
        spec as NumericalControlSpec
        val value = oldControl.getNumericalValue() ?: spec.defaultValue.get()
        val duration = (obj as? ScoreObject)?.duration ?: 1.0.asTime
        val env = ponticello.model.score.Envelope.constant(value, duration)
        val displayColor = reactiveVariable(spec.associatedColor)
        val display = reactiveVariable(true)
        return EnvelopeControl(env, displayColor, display)
    }

    override fun actions(
        namedControl: ParameterControlList.NamedParameterControl, control: EnvelopeControl, view: ScoreObjectView?,
    ): List<ContextualizedAction> = actions.withContext(control)

    override fun toString(): String = "Env"

    private val actions = collectActions<EnvelopeControl> {
        add(detailsAction(sceneFill = DEFAULT_SCENE_FILL.opacity(0.5)) { control ->
            CheckBox(control.display)
                .setupUndo(control.context[UndoManager], variableDescription = "Display envelope")
                .named("Display")
        })
    }
}