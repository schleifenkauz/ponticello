package ponticello.ui.midi

import hextant.context.Context
import ponticello.model.score.controls.ParameterControlList
import ponticello.model.score.controls.ValueControl
import ponticello.sc.NumericalControlSpec
import reaktive.value.now

class ParameterControlsMidiContext(
    context: Context, private val controls: () -> ParameterControlList?
) : AbstractMidiContext(context) {
    constructor(controls: ParameterControlList) : this(controls.context, { controls })

    override fun cc(channel: Int, index: Int, value: Int) {
        val ctrls = controls() ?: return
        val numericalControls = ctrls.all().filter { ctrl -> ctrl.now is ValueControl }
        if (index !in numericalControls.indices) return
        val control = numericalControls[index]
        val spec = control.spec.now as? NumericalControlSpec ?: return
        val variable = (control.now as ValueControl).value
        variable.adjustByMidiDelta(value, spec, context, "Adjust parameter")
    }
}