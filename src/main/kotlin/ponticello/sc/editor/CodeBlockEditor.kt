package ponticello.sc.editor

import hextant.core.editor.CompoundEditor
import ponticello.sc.CodeBlock
import reaktive.value.ReactiveValue

class CodeBlockEditor(
    val variables: IdentifierListEditor = IdentifierListEditor(),
    val statements: ScExprListEditor = ScExprListEditor()
) : CompoundEditor<CodeBlock>(), ScExprEditor<CodeBlock> {
    override lateinit var result: ReactiveValue<CodeBlock>
        private set

    init {
        addComponent(::variables)
        addComponent(::statements) { context.makeSubScope(variables, type = "var") }
    }

    override fun doInitialize() {
        super.doInitialize()
        result = composeResult { CodeBlock(variables.now, statements.now) }
    }
}