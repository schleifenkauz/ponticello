package xenakis.sc.view

import bundles.Bundle
import bundles.createBundle
import bundles.publicProperty
import hextant.codegen.ProvideImplementation
import hextant.context.ControlFactory
import hextant.core.view.CompoundEditorControl
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
        supportArguments(SINGLE_LINE_FUNCTION)
    }

    override fun build(): Layout = if (arguments[SINGLE_LINE_FUNCTION] && canBeSingleLine.now) horizontal {
        styleClass("compound-expr", "function", "function-singleline")
        viewHorizontal(editor.parameters)
        operator(" -> ")
        view(editor.body.statements.editors.now[0])
    } else vertical {
        styleClass("compound-expr", "function", "function-multiline")
        horizontal {
            keyword("arg")
            space()
            viewHorizontal(editor.parameters)
        }
        CodeBlockEditorControl.displayVarsAndStatements(this@vertical, editor.body)
    }

    companion object {
        val SINGLE_LINE_FUNCTION = publicProperty("SINGLE_LINE_FUNCTION", false)
    }
}

