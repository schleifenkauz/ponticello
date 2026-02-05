package ponticello.ui.midi

import fxutils.undo.UndoManager
import fxutils.undo.VariableEdit
import hextant.context.Context
import ponticello.impl.Decimal
import ponticello.impl.roundToNearestMultiple
import ponticello.impl.times
import ponticello.model.GlobalSettings
import ponticello.sc.NumericalControlSpec
import reaktive.value.Variable
import reaktive.value.now
import kotlin.math.pow

fun Decimal.adjustByMidiDelta(midiValue: Int, spec: NumericalControlSpec, context: Context): Decimal {
    //TODO make sensitivity dependent on ratio of range to step
    val knobSensitivity = context[GlobalSettings].knobSensitivity.now.toDouble()
    if (midiValue >= 64) {
        val factor = (128.0 - midiValue).pow(knobSensitivity)
        val delta = spec.step.get() * factor
        return (this - delta).coerceIn(spec.range)
    } else {
        val factor = midiValue.toDouble().pow(knobSensitivity)
        val delta = spec.step.get() * factor
        return (this + delta).coerceIn(spec.range)
    }
}

fun Variable<Decimal>.adjustByMidiDelta(
    midiValue: Int, spec: NumericalControlSpec,
    context: Context, actionDescription: String
) {
    val before = get()
    val after = before
        .adjustByMidiDelta(midiValue, spec, context)
        .roundToNearestMultiple(spec.step.get())
    VariableEdit.updateVariable(this, after, context[UndoManager], actionDescription)
}