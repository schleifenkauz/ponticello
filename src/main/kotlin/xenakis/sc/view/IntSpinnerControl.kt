package xenakis.sc.view

import bundles.Bundle
import bundles.publicProperty
import hextant.codegen.ProvideImplementation
import hextant.context.ControlFactory
import hextant.core.editor.SimpleEditor
import hextant.core.view.EditorControl
import javafx.scene.control.Spinner
import reaktive.value.now
import xenakis.sc.editor.SimpleIntegerEditor

class IntSpinnerControl @ProvideImplementation(ControlFactory::class) constructor(
    editor: SimpleIntegerEditor, arguments: Bundle
) : EditorControl<Spinner<Int>>(editor, arguments), SimpleEditor.View<Int> {
    private val spinner = Spinner<Int>(arguments[MIN], arguments[MAX], editor.result.now)

    init {
        spinner.valueProperty().addListener { _, _, newValue ->
            editor.setResult(newValue)
        }
        supportArguments(MIN, MAX)
    }

    override fun displayResult(result: Int) {
        spinner.valueFactory.value = result
    }

    override fun createDefaultRoot(): Spinner<Int> = spinner

    companion object {
        val MIN = publicProperty<Int>("spinner-max", 0)
        val MAX = publicProperty<Int>("spinner-min")
    }
}