package ponticello.sc.view

import bundles.Bundle
import bundles.publicProperty
import fxutils.controls.IntSpinner
import hextant.codegen.ProvideImplementation
import hextant.context.ControlFactory
import hextant.core.editor.SimpleEditor
import hextant.core.view.EditorControl
import ponticello.sc.editor.SimpleIntegerEditor
import reaktive.value.now

class IntSpinnerControl @ProvideImplementation(ControlFactory::class) constructor(
    editor: SimpleIntegerEditor, arguments: Bundle
) : EditorControl<IntSpinner>(editor, arguments), SimpleEditor.View<Int> {
    private val spinner = IntSpinner(arguments[MIN], arguments[MAX], editor.result.now)

    init {
        spinner.onUserInput { value -> editor.setResult(value) }
        supportArguments(MIN, MAX)
    }

    override fun displayResult(result: Int) {
        spinner.display(result)
    }

    override fun createDefaultRoot(): IntSpinner = spinner

    companion object {
        val MIN = publicProperty<Int>("spinner-max", 0)
        val MAX = publicProperty<Int>("spinner-min")
    }
}