package xenakis.model.obj

import hextant.context.Context
import hextant.serial.EditorRoot
import javafx.scene.paint.Color
import kotlinx.serialization.Serializable
import reaktive.list.MutableReactiveList
import reaktive.list.reactiveList
import reaktive.list.toReactiveList
import reaktive.value.ReactiveVariable
import reaktive.value.now
import reaktive.value.reactiveVariable
import xenakis.impl.ColorSerializer
import xenakis.impl.copy
import xenakis.impl.randomColor
import xenakis.model.Logger
import xenakis.model.registry.ProcessDefRegistry
import xenakis.sc.CodeBlock
import xenakis.sc.RawScExpr
import xenakis.sc.client.ScWriter
import xenakis.sc.client.SuperColliderClient
import xenakis.sc.editor.CodeBlockEditor
import xenakis.sc.substitute

@Serializable
class ProcessDefObject(
    override val mutableName: ReactiveVariable<String>,
    val color: ReactiveVariable<@Serializable(with = ColorSerializer::class) Color>,
    override val parameters: MutableReactiveList<ParameterDefObject>,
    val processCode: EditorRoot<CodeBlockEditor>
) : ConfigurableParameterizedObjectDef, AbstractSuperColliderObject() {
    override val superColliderName: String
        get() = "~proc_${name.now}"

    override val liveCycleType: SuperColliderObject.LiveCycleType
        get() = SuperColliderObject.LiveCycleType.InterpreterBoot

    override fun canRenameTo(newName: String): Boolean = !context[ProcessDefRegistry].has(newName)

    override fun ScWriter.allocateServerObject() {
        appendBlock("$superColliderName = ") {
            if (parameters.now.any()) +"arg ${parameters.now.joinToString(", ") { p -> p.name.now }}"
            +"var t = 0.0"
            val argumentSubstitution = parameters.now.associate { p ->
                val name = p.name.now
                name to RawScExpr("$name.value(t)")
            }
            val code = processCode.editor.result.now.substitute(argumentSubstitution) as CodeBlock
            code.writeCode(writer, context)
        }
    }

    override fun sync() {
        context[SuperColliderClient].run { sync(writer) }
        Logger.confirm("Synchronized SynthDef '${name.now}'", Logger.Category.Instruments)
    }

    override fun ScWriter.freeServerObject() {
        +"$superColliderName = nil"
    }

    override fun initialize(context: Context) {
        if (initialized) return
        super.initialize(context)
        for (parameter in parameters.now) {
            parameter.initialize(context)
        }
    }

    override fun rename(newName: String) {
        super.rename(newName)
        sync()
    }

    fun copy(name: String): ProcessDefObject = ProcessDefObject(
        reactiveVariable(name),
        color.copy(),
        parameters.now.map { p -> p.copy() }.toReactiveList(),
        processCode.clone()
    )

    companion object {
        fun newEmpty(name: String, context: Context) = ProcessDefObject(
            mutableName = reactiveVariable(name),
            color = reactiveVariable(randomColor()),
            parameters = reactiveList(),
            processCode = EditorRoot.create(CodeBlockEditor(context))
        )
    }
}