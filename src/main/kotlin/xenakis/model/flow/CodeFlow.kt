package xenakis.model.flow

import hextant.context.Context
import hextant.context.withoutUndo
import hextant.serial.EditorRoot
import kotlinx.serialization.Serializable
import reaktive.value.ReactiveString
import reaktive.value.binding.map
import reaktive.value.now
import xenakis.model.obj.BusObject
import xenakis.model.registry.BusRegistry
import xenakis.model.registry.ObjectReference
import xenakis.sc.client.ScWriter
import xenakis.sc.editor.CodeBlockEditor
import xenakis.sc.editor.IdentifierEditor
import xenakis.sc.editor.assign
import xenakis.sc.editor.`in`

@Serializable
class CodeFlow(
    private val busRef: ObjectReference,
    val codeEditor: EditorRoot<CodeBlockEditor>,
) : AudioFlow() {
    override lateinit var associatedBus: BusObject
        private set

    override lateinit var superColliderName: ReactiveString
        private set

    override fun initialize(context: Context) {
        super.initialize(context)
        busRef.resolve(context[BusRegistry])
        associatedBus = busRef.get()
        superColliderName = associatedBus.name.map { n -> "~flow_${n}_code" }
    }

    override fun copyFor(associatedBus: BusObject): AudioFlow = CodeFlow(associatedBus.reference(), codeEditor.clone())

    override fun ScWriter.writeCode(synthName: String, order: ScoreObjectInfo) {
        val code = codeEditor.editor.result.now
        appendBlock(endLine = false) {
            //TODO maybe read associatedBus into 'snd' variable here
            code.writeCode(writer, context)
        }
        +".play"
    }

    override fun getConnectedBusses(vararg flowType: FlowType): Set<BusObject> =
        if (FlowType.InOut in flowType) setOf(associatedBus) else emptySet()

    companion object {
        fun createFor(bus: BusObject, context: Context): CodeFlow {
            val editor = CodeBlockEditor(context)
            context.withoutUndo {
                editor.variables.addLast(IdentifierEditor(context, "snd"))
                editor.statements.addLast(assign("snd", `in`(context, bus)))
            }
            return CodeFlow(bus.reference(), EditorRoot.create(editor))
        }
    }
}