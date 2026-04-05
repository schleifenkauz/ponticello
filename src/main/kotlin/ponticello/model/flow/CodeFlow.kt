package ponticello.model.flow

import hextant.context.Context
import hextant.serial.EditorRoot
import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import ponticello.impl.writeCode
import ponticello.sc.client.ScWriter
import ponticello.sc.editor.CodeBlockEditor
import reaktive.value.ReactiveValue
import reaktive.value.ReactiveVariable
import reaktive.value.binding.map
import reaktive.value.now
import reaktive.value.reactiveVariable

@Serializable
@SerialName("CodeFlow")
class CodeFlow(val codeEditor: EditorRoot<@Contextual CodeBlockEditor>) : AudioFlow() {
    override val active = reactiveVariable(true)

    @SerialName("name")
    override var _name: ReactiveVariable<String>? = null

    override fun initialize(context: Context) {
        super.initialize(context)
        codeEditor.initialize(context)
    }

    override fun copy(): AudioFlow = CodeFlow(codeEditor.clone(context))

    override val isValid: ReactiveValue<Boolean> = codeEditor.editor.result.map { result -> result.isValid }

    override fun ScWriter.createObject() {
    }

    override fun writeCode(): String = writeCode(group = false) {
        append("CodeFlow('", name.now, "')")
        val code = codeEditor.editor.result.now
        appendBlock("$superColliderName = ", endLine = null) {
            code.writeCode(writer, context)
        }
    }

    companion object {
        fun create(): CodeFlow = CodeFlow(EditorRoot(CodeBlockEditor()))
    }
}