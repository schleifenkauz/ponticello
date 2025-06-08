package ponticello.sc.editor

import hextant.core.Editor
import hextant.core.editor.ChoiceEditor
import hextant.serial.JsonSerializer
import hextant.serial.KJsonSerializer
import ponticello.impl.randomColor
import ponticello.sc.*

class ControlSpecEditor : ChoiceEditor<ParameterType, ControlSpec, Editor<ControlSpec>>(),
    JsonSerializer<ParameterType> by KJsonSerializer.get() {
    override fun choices(): List<ParameterType> = ParameterType.regularTypes

    fun setResult(spec: ControlSpec) {
        when (spec) {
            is BufferControlSpec -> {
                val specEditor = BufferControlSpecEditor(
                    channels = SimpleIntegerEditor(spec.channels),
                    inlineDisplay = SimpleBooleanEditor(spec.inlineDisplay)
                )
                select(ParameterType.Buffer, specEditor)
            }

            is BusControlSpec -> {
                val specEditor = BusControlSpecEditor(
                    RateEditor(spec.rate),
                    SimpleIntegerEditor(spec.channels),
                    inlineDisplay = SimpleBooleanEditor(spec.inlineDisplay)
                )
                select(ParameterType.Bus, specEditor)
            }

            is NumericalControlSpec -> {
                val specEditor = NumericalControlSpecEditor(
                    defaultValue = DecimalLiteralEditor(spec.defaultValue.text),
                    min = DecimalLiteralEditor(spec.min.text),
                    max = DecimalLiteralEditor(spec.max.text),
                    step = DecimalLiteralEditor(spec.step.text),
                    lag = DecimalLiteralEditor(spec.lag.text),
                    warp = WarpEditor(spec.warp),
                    associatedColor = SimpleColorEditor(spec.associatedColor),
                    inlineDisplay = SimpleBooleanEditor(spec.inlineDisplay)
                )
                select(ParameterType.Numerical, specEditor)
            }

            is BufferPositionControlSpec -> {
                val specEditor = BufferPositionControlSpecEditor(
                    inlineDisplay = SimpleBooleanEditor(spec.inlineDisplay)
                )
                select(ParameterType.BufferPosition, specEditor)
            }

            else -> throw AssertionError("Illegal ControlSpec type: $spec")
        }
    }

    override fun createEditor(choice: ParameterType): Editor<ControlSpec> = when (choice) {
        ParameterType.Bus -> BusControlSpecEditor(
            RateEditor(Rate.Audio),
            SimpleIntegerEditor(2)
        )

        ParameterType.Buffer -> BufferControlSpecEditor()
        ParameterType.Numerical -> NumericalControlSpecEditor(
            defaultValue = DecimalLiteralEditor("0"),
            min = DecimalLiteralEditor("0"),
            max = DecimalLiteralEditor("1"),
            warp = WarpEditor(Warp.Linear),
            lag = DecimalLiteralEditor("0.02"),
            step = DecimalLiteralEditor("0.1"),
            associatedColor = SimpleColorEditor(randomColor())
        )

        else -> throw AssertionError("unknown parameter type $choice")
    }
}