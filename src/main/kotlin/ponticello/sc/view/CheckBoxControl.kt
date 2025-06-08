package ponticello.sc.view

import bundles.Bundle
import hextant.codegen.ProvideImplementation
import hextant.context.ControlFactory
import hextant.core.editor.SimpleEditor
import hextant.core.view.EditorControl
import javafx.scene.control.CheckBox
import ponticello.sc.editor.SimpleBooleanEditor

class CheckBoxControl @ProvideImplementation(ControlFactory::class) constructor(
    private val editor: SimpleBooleanEditor, arguments: Bundle,
) : EditorControl<CheckBox>(editor, arguments), SimpleEditor.View<Boolean> {
    private val content = CheckBox()

    init {
        editor.addView(this)
        content.selectedProperty().addListener { _, _, newValue ->
            editor.setResult(newValue)
        }
    }

    override fun createDefaultRoot(): CheckBox = content

    override fun displayResult(result: Boolean) {
        if (result == content.isSelected) return
        content.isSelected = result
    }
}