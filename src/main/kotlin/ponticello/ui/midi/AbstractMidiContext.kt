package ponticello.ui.midi

import hextant.context.Context
import ponticello.impl.Decimal
import ponticello.impl.times
import ponticello.model.Settings
import ponticello.sc.NumericalControlSpec
import reaktive.value.now
import kotlin.math.pow

abstract class AbstractMidiContext(private val context: Context) : MidiContext {
    private val knobSensitivity get() = context[Settings].knobSensitivity.now.toDouble()

    protected fun adjustValue(variable: Decimal, spec: NumericalControlSpec, midiValue: Int): Decimal {
        //TODO make sensitivity dependent on ratio of range to step
        if (midiValue >= 64) {
            val factor = (128.0 - midiValue).pow(knobSensitivity)
            val delta = spec.step.get() * factor
            return (variable - delta).coerceIn(spec.range)
        } else {
            val factor = midiValue.toDouble().pow(knobSensitivity)
            val delta = spec.step.get() * factor
            return (variable + delta).coerceIn(spec.range)
        }
    }
}