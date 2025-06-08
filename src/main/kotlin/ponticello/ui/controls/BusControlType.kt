package ponticello.ui.controls

import fxutils.actions.ContextualizedAction
import javafx.scene.Node
import javafx.scene.layout.Region
import ponticello.model.obj.ParameterizedObject
import ponticello.model.registry.BusRegistry
import ponticello.model.registry.reference
import ponticello.model.score.ParameterControlList
import ponticello.model.score.controls.BusControl
import ponticello.model.score.controls.ParameterControl
import ponticello.model.score.controls.getBus
import ponticello.sc.BusControlSpec
import ponticello.sc.ControlSpec
import ponticello.ui.actions.ServerActions
import ponticello.ui.score.ScoreObjectView
import reaktive.value.now
import reaktive.value.reactiveVariable

data object BusControlType : ControlType<BusControl>() {
    override fun applicableOn(obj: ParameterizedObject, spec: ControlSpec): Boolean = spec is BusControlSpec

    override fun createDetailInput(
        namedControl: ParameterControlList.NamedParameterControl,
        control: BusControl,
        view: ScoreObjectView?,
    ): Node = busSelector(control.bus, namedControl.spec.now, namedControl.context)

    override fun createSimpleInput(
        namedControl: ParameterControlList.NamedParameterControl, control: BusControl,
    ): Node = createDetailInput(namedControl, control, null)

    override fun createInitialControl(
        obj: ParameterizedObject,
        spec: ControlSpec?,
        oldControl: ParameterControl,
        namedControl: ParameterControlList.NamedParameterControl,
        anchorNode: Region,
    ): BusControl {
        val initial = oldControl.getBus() ?: obj.context[BusRegistry].getDefault().reference()
        return BusControl(reactiveVariable(initial))
    }

    override fun actions(
        namedControl: ParameterControlList.NamedParameterControl,
        control: BusControl,
        view: ScoreObjectView?,
    ): List<ContextualizedAction> = listOf(ServerActions.scopeBus.withContext(control.bus))

    override fun toString(): String = "Bus"
}