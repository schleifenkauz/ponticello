package xenakis.sc.editor

import hextant.context.Context
import hextant.context.withoutUndo
import hextant.core.editor.Expander
import reaktive.value.now
import xenakis.model.Settings
import xenakis.sc.*

class ParameterDefExpander(context: Context) : Expander<ParameterDef, ParameterDefEditor>(context) {
    constructor(context: Context, def: ParameterDef) : this(context) {
        withoutUndo { expand(createEditor(def)) }
    }

    override fun expand(text: String): ParameterDefEditor {
        val spec = context[Settings].getDefaultControlSpec(text)
        return if (spec != null) createEditor(ParameterDef(Identifier(text), spec))
        else ParameterDefEditor(context, IdentifierEditor(context, text))
    }

    override fun defaultResult(): ParameterDef = ParameterDef(Identifier(text.now!!), NumericalControlSpec.DEFAULT)

    override fun expand(completion: Any): ParameterDefEditor? {
        if (completion !is ParameterDef) return null
        val editor = createEditor(completion)
        return editor
    }

    private fun createEditor(def: ParameterDef): ParameterDefEditor = context.withoutUndo {
        val editor = ParameterDefEditor(context)
        editor.name.setText(def.name.text)
        when (val spec = def.spec) {
            is BufferControlSpec -> {
                val bufferRefEditor = BufferRefEditor(context)
                bufferRefEditor.select(spec.defaultValue)
                val specEditor = BufferControlSpecEditor(context, bufferRefEditor)
                editor.spec.select(ParameterType.Buffer, specEditor)
            }

            is BusControlSpec -> {
                val busRefEditor = BusRefEditor(context, Bus.output)
                busRefEditor.select(spec.defaultValue)
                val specEditor = BusControlSpecEditor(context, busRefEditor)
                editor.spec.select(ParameterType.Buffer, specEditor)
            }

            is NumericalControlSpec -> {
                val specEditor = spec.createEditor(context)
                editor.spec.select(ParameterType.Numerical, specEditor)
            }

            ControlSpecUnspecified -> {}
        }
        return editor
    }
}