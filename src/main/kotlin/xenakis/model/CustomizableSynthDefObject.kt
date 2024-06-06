package xenakis.model

import hextant.context.Context
import hextant.serial.EditorRoot
import javafx.scene.paint.Color
import kotlinx.serialization.Serializable
import reaktive.list.MutableReactiveList
import reaktive.list.reactiveList
import reaktive.value.ReactiveVariable
import reaktive.value.now
import reaktive.value.reactiveVariable
import xenakis.impl.ColorSerializer
import xenakis.impl.ScWriter
import xenakis.impl.SuperColliderClient
import xenakis.impl.randomColor
import xenakis.sc.*
import xenakis.sc.editor.CodeBlockEditor

@Serializable
class CustomizableSynthDefObject(
    override val mutableName: ReactiveVariable<String>,
    override val color: ReactiveVariable<@Serializable(with = ColorSerializer::class) Color>,
    override val parameters: MutableReactiveList<ParameterDefObject>,
    val ugenGraph: EditorRoot<CodeBlockEditor>
) : SynthDefObject, AbstractRenamableObject() {
    override fun sync(writer: ScWriter) {
        writer.allocateServerObject()
    }

    override fun ScWriter.allocateServerObject() {
        append("SynthDef(\\${name.now}, ")
        val parameterVariables = parameters.now.map { p -> Identifier(p.name.now) }
        val parameterAssignments = parameters.now.map { p ->
            val parameterCode = RawScExpr("\\${p.name.now}.${p.spec.now.code}")
            Assignment(Identifier(p.name.now), parameterCode)
        }
        val block = CodeBlock(
            variables = parameterVariables + ugenGraph.editor.result.now.variables,
            statements = parameterAssignments + ugenGraph.editor.result.now.statements
        )
        val graphFunc = ScFunction(emptyList(), block)
        graphFunc.code(this)
        appendLine(").add;")
    }

    override fun ScWriter.freeServerObject() {
        remove()
    }

    override fun remove() {
        context[SuperColliderClient].send("removeSynthDef", listOf(name))
    }

    override fun initialize(context: Context) {
        if (initialized) return
        super.initialize(context)
        for (parameter in parameters.now) {
            parameter.initialize(context)
        }
    }

    override fun canRenameTo(newName: String): Boolean = !context[InstrumentRegistry].has(newName)

    override fun rename(newName: String) {
        val client = context[SuperColliderClient]
        remove()
        super.rename(newName)
        client.run { sync(this) }
    }

    companion object {
        fun create(name: String, context: Context) = CustomizableSynthDefObject(
            mutableName = reactiveVariable(name),
            color = reactiveVariable(randomColor()),
            parameters = reactiveList(),
            ugenGraph = EditorRoot.create(CodeBlockEditor(context))
        )
    }
}