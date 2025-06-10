package ponticello.sc.view

import bundles.Bundle
import fxutils.controls.CheckBox
import hextant.codegen.ProvideImplementation
import hextant.context.ControlFactory
import hextant.core.editor.SimpleEditor
import hextant.core.view.EditorControl
import ponticello.sc.editor.SimpleBooleanEditor
import reaktive.Observer
import reaktive.value.now

class CheckBoxControl @ProvideImplementation(ControlFactory::class) constructor(
    private val editor: SimpleBooleanEditor, arguments: Bundle,
) : EditorControl<CheckBox>(editor, arguments), SimpleEditor.View<Boolean> {
    private val content = CheckBox()
    private val observer: Observer

    init {
        editor.addView(this)
        observer = content.state.observe { _, _, newValue ->
            editor.setResult(newValue)
        }
    }

    override fun createDefaultRoot(): CheckBox = content

    override fun displayResult(result: Boolean) {
        content.state.now = result
    }
}