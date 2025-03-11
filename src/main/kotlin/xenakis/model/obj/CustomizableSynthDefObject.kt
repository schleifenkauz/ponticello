package xenakis.model.obj

import hextant.context.Context
import hextant.context.withoutUndo
import hextant.core.EditorView
import hextant.core.editor.AbstractEditor
import hextant.serial.EditorRoot
import javafx.scene.paint.Color
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import reaktive.list.MutableReactiveList
import reaktive.list.reactiveList
import reaktive.list.toReactiveList
import reaktive.value.*
import xenakis.impl.ColorSerializer
import xenakis.impl.copy
import xenakis.impl.randomColor
import xenakis.model.Logger
import xenakis.model.flow.FlowType
import xenakis.model.registry.InstrumentRegistry
import xenakis.sc.*
import xenakis.sc.client.ScWriter
import xenakis.sc.client.SuperColliderClient
import xenakis.sc.editor.CodeBlockEditor

@Serializable
class CustomizableSynthDefObject(
    override val mutableName: ReactiveVariable<String>,
    override val parameters: MutableReactiveList<ParameterDefObject>,
    override val color: ReactiveVariable<@Serializable(with = ColorSerializer::class) Color> = reactiveVariable(Color.WHITE),
    val ugenGraph: EditorRoot<CodeBlockEditor>? = null
) : SynthDefObject, AbstractRenamableObject(), ConfigurableParameterizedObjectDef, InstrumentObject {
    @Transient
    private val editor = if (ugenGraph != null) SynthDefEditor(ugenGraph.editor.context, this) else null

    override fun copy(name: String): SynthDefObject = CustomizableSynthDefObject(
        reactiveVariable(name),
        parameters.now.map { p -> p.copy() }.toReactiveList(),
        color.copy(),
        context.withoutUndo { ugenGraph?.clone() }
    )

    override fun sync(writer: ScWriter) {
        writer.allocateServerObject()
    }

    override fun sync() {
        context[SuperColliderClient].run { sync(writer) }
        Logger.confirm("Synchronized SynthDef '${name.now}'", Logger.Category.Instruments)
    }

    override fun ScWriter.allocateServerObject() {
        append("SynthDef(\\${name.now}, ")
        val parameterVariables = parameters.now.map { p -> Identifier(p.name.now) }
        val parameterAssignments = parameters.now.map { p ->
            val parameterCode = RawScExpr("\\${p.name.now}.${p.spec.now.code}")
            Assignment(Identifier(p.name.now), parameterCode)
        }
        val freeAfterDuration =
            RawScExpr("Env.new(levels: [0, 0], times: [\\duration.ir]).kr(\\afterDuration.ir(Done.freeSelf))")
        val variables = ugenGraph?.editor?.result?.now?.variables.orEmpty()
        val statements = ugenGraph?.editor?.result?.now?.statements.orEmpty()
        val block = CodeBlock(
            variables = parameterVariables + variables + Identifier("duration"),
            statements = parameterAssignments + statements + freeAfterDuration
        )
        val graphFunc = ScFunction(emptyList(), block)
        graphFunc.code(this, context)
        appendLine(").add;")
    }

    override fun ScWriter.freeServerObject() {
        onRemoved()
    }

    override fun onRemoved() {
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
        onRemoved()
        super.rename(newName)
        context[SuperColliderClient].run { sync(this) }
    }

    class SynthDefEditor(
        context: Context, val obj: CustomizableSynthDefObject
    ) : AbstractEditor<SynthDefObject, EditorView>(context) {
        override val result: ReactiveValue<SynthDefObject>
            get() = reactiveValue(obj)

        init {
            if (obj.ugenGraph != null) addChild(obj.ugenGraph.editor)
        }
    }

    companion object {
        fun create(name: String, context: Context) = CustomizableSynthDefObject(
            mutableName = reactiveVariable(name),
            color = reactiveVariable(randomColor()),
            parameters = reactiveList(),
            ugenGraph = EditorRoot.create(CodeBlockEditor(context))
        )

        fun create(name: String, vararg parameters: ParameterDefObject) =
            CustomizableSynthDefObject(reactiveVariable(name), reactiveList(*parameters))

        fun sine() = create(
            name = "sine",
            ParameterDefObject("out", BusControlSpec(Rate.Audio, 2, FlowType.Out))
        )

        fun lpf() = create(
            name = "lpf",
            ParameterDefObject("bus", BusControlSpec(Rate.Audio, 2, FlowType.In))
        )
    }
}