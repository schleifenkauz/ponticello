package xenakis.model.obj

import bundles.publicProperty
import bundles.set
import hextant.context.Context
import hextant.context.SelectionDistributor
import hextant.context.extend
import hextant.core.editor.defaultState
import hextant.serial.EditorRoot
import javafx.scene.paint.Color
import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import reaktive.value.ReactiveVariable
import reaktive.value.now
import reaktive.value.reactiveVariable
import xenakis.impl.ColorSerializer
import xenakis.impl.Logger
import xenakis.impl.copy
import xenakis.impl.randomColor
import xenakis.model.flow.FlowType
import xenakis.model.registry.SynthDefRegistry
import xenakis.sc.*
import xenakis.sc.client.ScWriter
import xenakis.sc.client.SuperColliderClient
import xenakis.sc.editor.CodeBlockEditor
import xenakis.ui.registry.ParameterDefList

@Serializable
class CustomizableSynthDefObject(
    @SerialName("name") override val mutableName: ReactiveVariable<String>,
    override val parameters: ParameterDefList,
    override val color: ReactiveVariable<@Serializable(with = ColorSerializer::class) Color> = reactiveVariable(Color.WHITE),
    val ugenGraph: EditorRoot<@Contextual CodeBlockEditor>? = null,
) : SynthDefObject, AbstractRenamableObject(), ConfigurableParameterizedObjectDef {
    override val canCopy: Boolean
        get() = true

    override fun copy(name: String): SynthDefObject = CustomizableSynthDefObject(
        reactiveVariable(name),
        ParameterDefList(parameters.mapTo(mutableListOf()) { p -> p.copy() }),
        color.copy(),
        ugenGraph?.clone()
    )

    override fun ScWriter.sync() {
        appendBlock("fork") {
            createObject()
            +"s.sync"
            +"~xenakis_addr.sendMsg('/updated', 'synth_def:${name.now}')"
        }
    }

    override fun sync() {
        context[SuperColliderClient].run { sync() }
        Logger.confirm("Synchronized SynthDef '${name.now}'", Logger.Category.Instruments)
    }

    override fun ScWriter.createObject() {
        append("SynthDef(\\${name.now}, ")
        val parameterVariables = parameters.map { p -> Identifier(p.name.now) } + Identifier("duration")
        val parameterAssignments = parameters.map { p ->
            val parameterCode = RawScExpr("\\${p.name.now}.${p.spec.now.code}")
            Assignment(Identifier(p.name.now), parameterCode)
        } + Assignment(Identifier("duration"), SymbolLiteral("duration").send("ir"))
        val variables = ugenGraph?.editor?.result?.now?.variables.orEmpty()
        val statements = ugenGraph?.editor?.result?.now?.statements.orEmpty()
        val block = CodeBlock(
            variables = parameterVariables + variables,
            statements = parameterAssignments + statements
        )
        val graphFunc = ScFunction(emptyList(), block)
        graphFunc.code(this, context)
        appendLine(").add;")
    }

    override fun ScWriter.freeObject() {
        onRemoved()
    }

    override fun onRemoved() {
        context[SuperColliderClient].send("removeSynthDef", listOf(name.now))
    }

    override fun initialize(context: Context) {
        if (initialized) return
        val myContext = context.extend {
            set(editedSynthDef, this@CustomizableSynthDefObject)
            set(SelectionDistributor, SelectionDistributor.newInstance())
        }
        super.initialize(myContext)
        parameters.initialize(myContext)
        ugenGraph?.initialize(myContext)
    }

    override fun canRenameTo(newName: String): Boolean = !context[SynthDefRegistry].has(newName)

    override fun rename(newName: String) {
        onRemoved()
        super.rename(newName)
        context[SuperColliderClient].run { this.sync() }
    }

    companion object {
        val editedSynthDef = publicProperty<CustomizableSynthDefObject>("edited synth def")

        fun create(name: String) = CustomizableSynthDefObject(
            mutableName = reactiveVariable(name),
            color = reactiveVariable(randomColor()),
            parameters = ParameterDefList(),
            ugenGraph = EditorRoot(CodeBlockEditor().defaultState())
        )

        fun create(name: String, vararg parameters: ParameterDefObject) =
            CustomizableSynthDefObject(reactiveVariable(name), ParameterDefList(parameters.toMutableList()))

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