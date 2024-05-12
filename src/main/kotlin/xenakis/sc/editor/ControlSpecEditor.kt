package xenakis.sc.editor

import hextant.context.Context
import hextant.core.Editor
import hextant.core.editor.ChoiceEditor
import xenakis.sc.ControlSpec
import xenakis.sc.ParameterType
import xenakis.sc.Warp

class ControlSpecEditor(context: Context) :
    ChoiceEditor<ParameterType, ControlSpec, Editor<ControlSpec>>(context, default = ParameterType.Numerical) {
    override fun choices(): List<ParameterType> =
        listOf(ParameterType.Bus, ParameterType.Buffer, ParameterType.Numerical)

    override fun createEditor(choice: ParameterType): Editor<ControlSpec> = when (choice) {
        ParameterType.Bus -> BusControlSpecEditor(context)
        ParameterType.Buffer -> BufferControlSpecEditor(context)
        ParameterType.Numerical -> NumericalControlSpecEditor(
            context,
            defaultValue = DoubleLiteralEditor(context, "0"),
            min = DoubleLiteralEditor(context, "0"),
            max = DoubleLiteralEditor(context, "1"),
            warp = WarpEditor(context, Warp.Linear),
            step = DoubleLiteralEditor(context, "0.1"),
        )

        else -> throw AssertionError("unknown parameter type $choice")
    }
}