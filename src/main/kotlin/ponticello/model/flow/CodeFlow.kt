package ponticello.model.flow

import hextant.context.Context
import hextant.serial.EditorRoot
import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import ponticello.impl.writeCode
import ponticello.sc.editor.CodeBlockEditor
import reaktive.value.ReactiveValue
import reaktive.value.binding.map
import reaktive.value.now

@Serializable
@SerialName("CodeFlow")
class CodeFlow(val codeEditor: EditorRoot<@Contextual CodeBlockEditor>) : AudioFlow() {
    override fun initialize(context: Context) {
        super.initialize(context)
        codeEditor.initialize(context)
    }

    override fun copy(): AudioFlow = CodeFlow(codeEditor.clone(context))

    override val isValid: ReactiveValue<Boolean> = codeEditor.editor.result.map { result -> result.isValid }

    override fun writeCode(placement: NodePlacement): String = writeCode {
        val code = codeEditor.editor.result.now
        appendBlock("$superColliderName = ", endLine = false) {
            code.writeCode(writer, context)
        }
        +".play"
        if (!isActive.now) {
            +"$superColliderName.run(false)"
        }
    }

    companion object {
        fun create(): CodeFlow = CodeFlow(EditorRoot(CodeBlockEditor()))
    }
}