package xenakis.sc.view

import bundles.Bundle
import bundles.createBundle
import bundles.publicProperty
import bundles.set
import hextant.codegen.ProvideImplementation
import hextant.context.ControlFactory
import hextant.core.view.CompoundEditorControl
import hextant.core.view.ListEditorControl.Companion.ADD_WITH_COMMA
import hextant.core.view.ListEditorControl.Companion.CELL_FACTORY
import hextant.core.view.ListEditorControl.Companion.ORIENTATION
import hextant.core.view.ListEditorControl.Orientation.Horizontal
import hextant.core.view.ListEditorControl.SeparatorCell
import hextant.fx.view
import reaktive.collection.binding.isEmpty
import reaktive.collection.binding.size
import reaktive.value.binding.and
import reaktive.value.binding.equalTo
import reaktive.value.now

class ScFunctionEditorControl @ProvideImplementation(ControlFactory::class) constructor(
    private val editor: xenakis.sc.editor.ScFunctionEditor,
    arguments: Bundle
) : CompoundEditorControl(editor, arguments) {
    constructor(editor: xenakis.sc.editor.ScFunctionEditor): this(editor, createBundle())

    val canBeSingleLine =
        editor.body.statements.editors.size.equalTo(1) and editor.body.variables.editors.isEmpty()

    init {
        triggerLayoutOnChange(canBeSingleLine)
    }

    override fun build(): Layout = if (arguments[SINGLE_LINE_FUNCTION] && canBeSingleLine.now) horizontal {
        styleClass("compound-expr", "function", "function-singleline")
        viewParameters()
        operator(" -> ")
        view(editor.body.statements.editors.now[0])
    } else vertical {
        styleClass("compound-expr", "function", "function-multiline")
        horizontal {
            keyword("arg")
            space()
            viewParameters()
        }
        CodeBlockEditorControl.displayVarsAndStatements(this@vertical, editor.body)
    }

    private fun Horizontal.viewParameters() {
        view(editor.parameters) {
            set(ORIENTATION, Horizontal)
            set(CELL_FACTORY) { SeparatorCell(", ") }
            set(ADD_WITH_COMMA, true)
        }
    }

    companion object {
        val SINGLE_LINE_FUNCTION = publicProperty("SINGLE_LINE_FUNCTION", false)
    }
}

