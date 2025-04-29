package xenakis.sc.editor

import hextant.core.Editor
import hextant.core.editor.ChoiceEditor
import hextant.serial.JsonSerializer
import hextant.serial.KJsonSerializer
import xenakis.impl.randomColor
import xenakis.sc.*

class ControlSpecEditor : ChoiceEditor<ParameterType, ControlSpec, Editor<ControlSpec>>(),
    JsonSerializer<ParameterType> by KJsonSerializer.get() {
    override fun choices(): List<ParameterType> = ParameterType.regularTypes

    fun setResult(spec: ControlSpec) {
        when (spec) {
            is BufferControlSpec -> {
                val specEditor = BufferControlSpecEditor(channels = SimpleIntegerEditor(spec.channels))
                specEditor.setupDefaultState()
                select(ParameterType.Buffer, specEditor)
            }

            is BusControlSpec -> {
                val specEditor = BusControlSpecEditor(
                    RateEditor(spec.rate),
                    SimpleIntegerEditor(spec.channels),
                )
                specEditor.setupDefaultState()
                select(ParameterType.Bus, specEditor)
            }

            is NumericalControlSpec -> {
                val specEditor = spec.createEditor()
                select(ParameterType.Numerical, specEditor)
            }

            else -> {}
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
            step = DecimalLiteralEditor("0.1"),
            associatedColor = SimpleColorEditor(randomColor())
        )

        else -> throw AssertionError("unknown parameter type $choice")
    }
}