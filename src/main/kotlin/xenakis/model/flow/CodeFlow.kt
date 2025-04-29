package xenakis.model.flow

import hextant.context.Context
import hextant.serial.EditorRoot
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import reaktive.value.ReactiveString
import reaktive.value.ReactiveValue
import reaktive.value.binding.map
import reaktive.value.now
import reaktive.value.reactiveValue
import xenakis.sc.client.ScWriter
import xenakis.sc.editor.CodeBlockEditor

@Serializable
class CodeFlow(val codeEditor: EditorRoot<@Contextual CodeBlockEditor>) : AudioFlow() {
    override fun initialize(context: Context) {
        super.initialize(context)
        codeEditor.initialize(context)
    }

    override fun copy(): AudioFlow = CodeFlow(codeEditor.clone(context))

    override val isValid: ReactiveValue<Boolean> = codeEditor.editor.result.map { result -> result.isValid }

    override fun writeCode(writer: ScWriter, placement: NodePlacement) = with(writer) {
        val code = codeEditor.editor.result.now
        appendBlock(endLine = false) {
            code.writeCode(writer, context)
        }
        +".play"
    }

    override fun getDefaultName(): ReactiveString = reactiveValue("Code")

    companion object {
        fun create(): CodeFlow = CodeFlow(EditorRoot(CodeBlockEditor()))
    }
}