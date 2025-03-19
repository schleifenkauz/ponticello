package xenakis.model.flow

import hextant.context.Context
import hextant.serial.EditorRoot
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import reaktive.value.now
import xenakis.model.obj.BusObject
import xenakis.sc.client.ScWriter
import xenakis.sc.editor.CodeBlockEditor
import xenakis.sc.editor.IdentifierEditor
import xenakis.sc.editor.assign
import xenakis.sc.editor.`in`

@Serializable
class CodeFlow(val codeEditor: EditorRoot<@Contextual CodeBlockEditor>) : AudioFlow() {
    override fun initialize(context: Context, bus: BusObject) {
        super.initialize(context)
        codeEditor.initialize(context)
    }

    override fun copy(): AudioFlow = CodeFlow(codeEditor.clone(context))

    override fun getInputs(): Collection<BusObject> = emptySet()

    override fun getOutputs(): Collection<BusObject> = emptySet() //TODO make input ands output configurable

    override fun addListener(listener: AudioNode.Listener) {

    }

    override fun ScWriter.writeCode(placement: NodePlacement) {
        val code = codeEditor.editor.result.now
        appendBlock(endLine = false) {
            //TODO maybe read associatedBus into 'snd' variable here
            code.writeCode(writer, context)
        }
        +".play"
    }

    override fun getDefaultName(): String = "Code"

    companion object {
        fun createFor(bus: BusObject): CodeFlow {
            val editor = CodeBlockEditor()
            editor.variables.setInitialEditors(IdentifierEditor("snd"))
            editor.statements.setInitialEditors(assign("snd", `in`(bus)))
            return CodeFlow(EditorRoot(editor))
        }
    }
}