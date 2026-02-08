package ponticello.ui.midi

import fxutils.undo.UndoManager
import fxutils.undo.VariableEdit
import hextant.context.Context
import ponticello.impl.Decimal
import ponticello.impl.roundToInt
import ponticello.impl.times
import ponticello.model.GlobalSettings
import ponticello.sc.NumericalControlSpec
import reaktive.value.Variable
import reaktive.value.now
import kotlin.math.pow
import kotlin.math.roundToInt

fun Decimal.adjustByMidiDelta(midiValue: Int, spec: NumericalControlSpec, context: Context): Decimal {
    //TODO make sensitivity dependent on ratio of range to step
    val knobSensitivity = context[GlobalSettings].knobSensitivity.now.toDouble()
    val factor =
        if (midiValue >= 64) -(128.0 - midiValue).pow(knobSensitivity)
        else midiValue.toDouble().pow(knobSensitivity)
    val step = spec.step.get()
    val v = (this / step).roundToInt() + factor.roundToInt()
    return (v * step).coerceIn(spec.range)
}

fun Variable<Decimal>.adjustByMidiDelta(
    midiValue: Int, spec: NumericalControlSpec,
    context: Context, actionDescription: String
) {
    val before = get()
    val after = before.adjustByMidiDelta(midiValue, spec, context)
    VariableEdit.updateVariable(this, after, context[UndoManager], actionDescription)
}