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
        return Companion.expand(text, context)
    }

    override fun defaultResult(): ParameterDef = ParameterDef(Identifier(text.now!!), NumericalControlSpec.DEFAULT)

    override fun expand(completion: Any): ParameterDefEditor? {
        if (completion !is ParameterDef) return null
        val editor = createEditor(completion)
        return editor
    }

    private fun createEditor(def: ParameterDef): ParameterDefEditor = context.withoutUndo {
        return Companion.createEditor(def, context)
    }

    companion object {
        fun expand(name: String, context: Context): ParameterDefEditor {
            val spec = context[Settings].getDefaultControlSpec(name)
            return if (spec != null) createEditor(ParameterDef(Identifier(name), spec), context)
            else {
                val editor = ParameterDefEditor(context, IdentifierEditor(context, name))
                editor.spec.select(ParameterType.Numerical)
                editor
            }
        }

        private fun createEditor(def: ParameterDef, context: Context): ParameterDefEditor {
            val editor = ParameterDefEditor(context)
            editor.name.setText(def.name.text)
            when (val spec = def.spec) {
                is BufferControlSpec -> {
                    val bufferSelector = BufferSelector(context)
                    bufferSelector.select(spec.defaultValue)
                    val specEditor = BufferControlSpecEditor(context, bufferSelector)
                    editor.spec.select(ParameterType.Buffer, specEditor)
                }

                is BusControlSpec -> {
                    val busSelector = BusSelector(context, Bus.output)
                    busSelector.select(spec.defaultValue)
                    val specEditor = BusControlSpecEditor(context, busSelector)
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
}