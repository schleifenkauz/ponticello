package ponticello.sc.view

import bundles.Bundle
import hextant.core.view.CompoundEditorControl

class AdhocSynthEditorControl (
    private val editor: ponticello.sc.editor.AdhocSynthEditor, args: Bundle
) : CompoundEditorControl(editor, args) {
    override fun build(): Layout = vertical {
        horizontal {
            keyword("synth: ")
            view(editor.name)
            space()
            keyword("group: ")
        }
        view(editor.block)
    }
}