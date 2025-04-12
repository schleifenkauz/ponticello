package xenakis.sc.view

import bundles.Bundle
import fxutils.setFixedWidth
import hextant.codegen.ProvideImplementation
import hextant.context.ControlFactory
import hextant.core.editor.SimpleEditor
import hextant.core.view.EditorControl
import javafx.scene.control.ColorPicker
import javafx.scene.paint.Color
import reaktive.value.now
import xenakis.sc.editor.SimpleColorEditor

class ColorPickerControl @ProvideImplementation(ControlFactory::class) constructor(
    editor: SimpleColorEditor, arguments: Bundle,
) : EditorControl<ColorPicker>(editor, arguments), SimpleEditor.View<Color> {
    private val picker = ColorPicker(editor.result.now).setFixedWidth(30.0)

    init {
        picker.valueProperty().addListener { _, _, newValue ->
            editor.setResult(newValue)
        }
    }

    override fun displayResult(result: Color) {
        picker.value = result
    }

    override fun createDefaultRoot(): ColorPicker = picker
}