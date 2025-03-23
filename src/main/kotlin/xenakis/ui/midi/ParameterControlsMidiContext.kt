package xenakis.ui.midi

import reaktive.value.now
import xenakis.model.score.ParameterControlList
import xenakis.model.score.ValueControl
import xenakis.sc.NumericalControlSpec

class ParameterControlsMidiContext(private val controls: ParameterControlList) : AbstractMidiContext(controls.context) {
    override fun cc(channel: Int, index: Int, value: Int) {
        val numericalControls = controls.all().filter { ctrl -> ctrl.now is ValueControl }
        if (index !in numericalControls.indices) return
        val control = numericalControls[index]
        val spec = control.spec.now as? NumericalControlSpec ?: return
        val variable = (control.now as ValueControl).value
        variable.now = adjustValue(variable.now, spec, value)
    }
}