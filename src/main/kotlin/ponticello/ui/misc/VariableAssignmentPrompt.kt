package ponticello.ui.misc

import fxutils.prompt.CompoundPrompt
import hextant.context.Context
import hextant.context.createControl
import hextant.core.editor.defaultState
import ponticello.sc.ScExpr
import ponticello.sc.editor.ScExprExpander
import reaktive.value.now

class VariableAssignmentPrompt(
    variables: Set<String>, context: Context
) : CompoundPrompt<Map<String, ScExpr>>("Assign unbound variables") {
    private val editors = mutableMapOf<String, ScExprExpander>()

    init {
        for (variable in variables) {
            val editor = ScExprExpander(variable).defaultState()
            editor.initialize(context)
            editors[variable] = editor
            val control = context.createControl(editor)
            addItem(variable, control)
        }
        //val results = editors.values.map { ed -> ed.result }
        //val allValid = binding(dependencies(results)) { results.all { it.now.isValid } }
        //confirmButton.disableIf(allValid.not())
    }

    override fun confirm(): Map<String, ScExpr> = editors.mapValues { it.value.result.now }
}