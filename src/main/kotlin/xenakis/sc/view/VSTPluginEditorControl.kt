package xenakis.sc.view

import bundles.Bundle
import bundles.set
import fxutils.actions.button
import fxutils.centerChildren
import fxutils.setFixedWidth
import fxutils.styleClass
import hextant.codegen.ProvideImplementation
import hextant.context.ControlFactory
import hextant.core.view.CompoundEditorControl
import hextant.fx.view
import org.kordamp.ikonli.material2.Material2MZ
import org.kordamp.ikonli.materialdesign2.MaterialDesignE
import xenakis.sc.editor.VSTPluginEditor

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
        add(MaterialDesignE.EYE.button(action = "Configure plugin") {
            editor.configurePlugin()
        }.styleClass("medium-icon-button"))
        space()
        add(Material2MZ.SAVE.button(action = "Save configuration") {
            editor.saveConfiguration()
        }.styleClass("medium-icon-button"))
        root.centerChildren()
    }
}