package xenakis.sc.view

import bundles.Bundle
import bundles.set
import hextant.codegen.ProvideImplementation
import hextant.context.ControlFactory
import hextant.core.view.CompoundEditorControl
import hextant.fx.view
import xenakis.sc.editor.VSTPluginEditor
import xenakis.ui.Icon
import xenakis.ui.centerChildren
import xenakis.ui.setFixedWidth

class VSTPluginEditorControl @ProvideImplementation(ControlFactory::class) constructor(
    private val editor: VSTPluginEditor,
    args: Bundle
) : CompoundEditorControl(editor, args) {
    override fun build(): Layout = horizontal {
        styleClass("compound-expr", "plugin")
        keyword("plugin: ")
        view(editor.id)
        space()
        keyword("in: ")
        view(editor.input)
        space()
        keyword("channels: ")
        view(editor.channels) {
            set(IntSpinnerControl.MIN, 1)
            set(IntSpinnerControl.MAX, 12)
        }.setFixedWidth(70.0)
        space()
        add(Icon.View.button(action = "Configure plugin") {
            editor.configurePlugin()
        })
        space()
        add(Icon.Save.button(action = "Save configuration") {
            editor.saveConfiguration()
        })
        root.centerChildren()
    }
}