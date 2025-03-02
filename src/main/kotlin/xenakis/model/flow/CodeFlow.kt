package xenakis.model.flow

import hextant.context.Context
import hextant.serial.EditorRoot
import reaktive.value.ReactiveString
import reaktive.value.binding.map
import reaktive.value.now
import xenakis.model.obj.BusObject
import xenakis.model.registry.BusRegistry
import xenakis.model.registry.ObjectReference
import xenakis.sc.client.ScWriter
import xenakis.sc.editor.CodeBlockEditor

class CodeFlow(
    private val busRef: ObjectReference,
    val codeEditor: EditorRoot<CodeBlockEditor>,
) : AudioFlow() {
    override lateinit var associatedBus: BusObject
        private set

    override lateinit var name: ReactiveString
        private set

    override fun initialize(context: Context) {
        super.initialize(context)
        busRef.resolve(context[BusRegistry])
        associatedBus = busRef.get()
        name = associatedBus.name.map { n -> "${n}_code" }
    }

    override fun copyFor(associatedBus: BusObject): AudioFlow = CodeFlow(associatedBus.reference(), codeEditor.clone())

    override fun ScWriter.writeCode(synthName: String, order: SynthOrder) {
        val code = codeEditor.editor.result.now
        appendBlock(endLine = false) {
            //TODO maybe read associatedBus into 'snd' variable here
            code.writeCode(writer, context)
        }
        +".play"
    }

    override fun getConnectedBusses(vararg flowType: FlowType): Set<BusObject> =
        if (FlowType.InOut in flowType) setOf(associatedBus) else emptySet()
}