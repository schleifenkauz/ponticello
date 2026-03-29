package ponticello.sc.editor

import hextant.core.editor.CompoundEditor
import ponticello.sc.ScFunction
import reaktive.value.ReactiveValue

class ScFunctionEditor(
    val parameters: IdentifierListEditor = IdentifierListEditor(),
    val body: CodeBlockEditor = CodeBlockEditor()
) : CompoundEditor<ScFunction>(), ScExprEditor<ScFunction> {
    override lateinit var result: ReactiveValue<ScFunction>
        private set

    init {
        addComponent(::parameters)
        addComponent(::body) { context.makeSubScope(parameters, "arg") }
    }

    override fun doInitialize() {
        super.doInitialize()
        result = composeResult { ScFunction(parameters.now, body.now) }
    }
}