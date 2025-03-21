package xenakis.ui.midi

import reaktive.value.now
import xenakis.impl.times
import xenakis.model.Settings
import xenakis.model.score.ConstantControl
import xenakis.model.score.ParameterControls
import xenakis.sc.NumericalControlSpec
import kotlin.math.pow

class ParameterControlsMidiContext(private val controls: ParameterControls) : MidiContext {
    val knobSensitivity get() = controls.context[Settings].knobSensitivity.now.toDouble()

    override fun cc(channel: Int, index: Int, value: Int) {
        val numericalControls = controls.all().filter { ctrl -> ctrl.now is ConstantControl }
        if (index - 20 !in numericalControls.indices) return
        val control = numericalControls[index - 20]
        val spec = control.spec.now as? NumericalControlSpec ?: return
        val variable = (control.now as ConstantControl).value
        if (value >= 64) {
            variable.now = (variable.now - spec.step.get() * (128.0 - value).pow(knobSensitivity)).coerceIn(spec.range)
        } else {
            variable.now = (variable.now + spec.step.get() * value.toDouble().pow(knobSensitivity)).coerceIn(spec.range)
        }
    }
}