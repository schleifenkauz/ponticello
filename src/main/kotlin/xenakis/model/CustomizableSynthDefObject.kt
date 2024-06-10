package xenakis.model

import hextant.context.Context
import hextant.core.EditorView
import hextant.core.editor.AbstractEditor
import hextant.serial.EditorRoot
import javafx.scene.paint.Color
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import reaktive.list.MutableReactiveList
import reaktive.list.reactiveList
import reaktive.value.*
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
    @Transient
    private val editor = SynthDefEditor(ugenGraph.editor.context, this)

    override fun sync(writer: ScWriter) {
        writer.allocateServerObject()
    }

    override fun sync() {
        context[SuperColliderClient].run { sync(writer) }
    }

    override fun ScWriter.allocateServerObject() {
        append("SynthDef(\\${name.now}, ")
        val parameterVariables = parameters.now.map { p -> Identifier(p.name.now) }
        val parameterAssignments = parameters.now.map { p ->
            val parameterCode = RawScExpr("\\${p.name.now}.${p.spec.now.code}")
            Assignment(Identifier(p.name.now), parameterCode)
        }
        val getDuration = RawScExpr("duration = \\duration.ir")
        val freeAfterDuration = RawScExpr("Env.new(levels: [0, 0], times: [duration]).kr(Done.freeSelf)")
        val block = CodeBlock(
            variables = parameterVariables + ugenGraph.editor.result.now.variables + Identifier("duration"),
            statements = parameterAssignments + getDuration + ugenGraph.editor.result.now.statements + freeAfterDuration
        )
        val graphFunc = ScFunction(emptyList(), block)
        graphFunc.code(this)
        appendLine(").add;")
    }

    override fun ScWriter.freeServerObject() {
        remove()
    }

    override fun remove() {
        context[SuperColliderClient].send("removeSynthDef", listOf(name.now))
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
        remove()
        super.rename(newName)
        context[SuperColliderClient].run { sync(this) }
    }

    class SynthDefEditor(
        context: Context, val obj: CustomizableSynthDefObject
    ) : AbstractEditor<SynthDefObject, EditorView>(context) {
        override val result: ReactiveValue<SynthDefObject>
            get() = reactiveValue(obj)

        init {
            addChild(obj.ugenGraph.editor)
        }
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