package xenakis.sc.view

import bundles.Bundle
import hextant.codegen.ProvideImplementation
import hextant.context.ControlFactory
import hextant.core.view.CompoundEditorControl
import xenakis.ui.centerChildrenVertically

class VariableEditorControl @ProvideImplementation(ControlFactory::class) constructor(
    private val editor: xenakis.sc.editor.VariableEditor,
    arguments: Bundle
) : CompoundEditorControl(editor, arguments) {
    override fun build(): Layout = horizontal {
        styleClass("compound-expr", "variable")
        view(editor.name)
        operator(" = ")
        view(editor.defaultValue)
        root.centerChildrenVertically()
    }
}