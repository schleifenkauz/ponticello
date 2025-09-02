package ponticello.ui.midi

import ponticello.model.score.ParameterControlList
import ponticello.model.score.controls.ValueControl
import ponticello.sc.NumericalControlSpec
import reaktive.value.now

class ParameterControlsMidiContext(private val controls: ParameterControlList) : AbstractMidiContext(controls.context) {
    override fun cc(channel: Int, index: Int, value: Int) {
        val numericalControls = controls.all().filter { ctrl -> ctrl.now is ValueControl }
        if (index !in numericalControls.indices) return
        val control = numericalControls[index]
        val spec = control.spec.now as? NumericalControlSpec ?: return
        val variable = (control.now as ValueControl).value
        variable.adjustByMidiDelta(value, spec, context)
    }
}