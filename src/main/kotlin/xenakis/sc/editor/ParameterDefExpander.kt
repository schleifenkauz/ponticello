package xenakis.sc.editor

import hextant.context.Context
import hextant.core.editor.ColorEditor
import hextant.core.editor.Expander
import reaktive.value.now
import xenakis.sc.*

class ParameterDefExpander(context: Context) : Expander<ParameterDef, ParameterDefEditor>(context) {
    override fun expand(text: String): ParameterDefEditor =
        ParameterDefEditor(context, name = IdentifierEditor(context, text))

    override fun defaultResult(): ParameterDef =
        ParameterDef(Identifier(text.now!!), NumericalControlSpec(0.0, 1.0, 0.0, Warp.Linear, 0.1))

    override fun expand(completion: Any): ParameterDefEditor? {
        if (completion !is ParameterDef) return null
        val editor = ParameterDefEditor(context)
        editor.name.setText(completion.name.text)
        when (completion.spec) {
            is BufferControlSpec -> {
                val bufferRefEditor = BufferRefEditor(context)
                bufferRefEditor.select(completion.spec.defaultValue)
                val specEditor = BufferControlSpecEditor(context, bufferRefEditor)
                editor.spec.select(ParameterType.Buffer, specEditor)
            }

            is BusControlSpec -> {
                val busRefEditor = BusRefEditor(context, Bus.output)
                busRefEditor.select(completion.spec.defaultValue)
                val specEditor = BusControlSpecEditor(context, busRefEditor)
                editor.spec.select(ParameterType.Buffer, specEditor)
            }

            is NumericalControlSpec -> {
                val specEditor = completion.spec.createEditor(context)
                editor.spec.select(ParameterType.Numerical, specEditor)
            }

            ControlSpecUnspecified -> {}
        }
        return editor
    }
}