package xenakis.sc.view

import bundles.Bundle
import hextant.codegen.ProvideImplementation
import hextant.context.ControlFactory
import hextant.core.view.CompoundEditorControl

class AdhocSynthEditorControl @ProvideImplementation(ControlFactory::class) constructor(
    private val editor: xenakis.sc.editor.AdhocSynthEditor, args: Bundle
) : CompoundEditorControl(editor, args) {
    override fun build(): Layout = vertical {
        horizontal {
            keyword("synth: ")
            view(editor.name)
            space()
            keyword("group: ")
            view(editor.group)
        }
        view(editor.block)
    }
}