package ponticello.model.obj

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
import ponticello.impl.ColorSerializer
import ponticello.impl.Logger
import ponticello.impl.copy
import ponticello.impl.randomColor
import ponticello.model.registry.SynthDefRegistry
import ponticello.model.score.controls.AttackReleaseControl
import ponticello.sc.*
import ponticello.sc.client.ScWriter
import ponticello.sc.client.SuperColliderClient
import ponticello.sc.editor.CodeBlockEditor
import ponticello.ui.registry.ParameterDefList
import reaktive.value.ReactiveVariable
import reaktive.value.now
import reaktive.value.reactiveVariable

@Serializable
class CustomizableSynthDefObject(
    @SerialName("name") override val mutableName: ReactiveVariable<String>,
    override val parameters: ParameterDefList,
    override val color: ReactiveVariable<@Serializable(with = ColorSerializer::class) Color> = reactiveVariable(Color.WHITE),
    val ugenGraph: EditorRoot<@Contextual CodeBlockEditor>? = null,
) : SynthDefObject, AbstractRenamableObject(), ConfigurableParameterizedObjectDef {
    override val canCopy: Boolean
        get() = true

    override fun allParameters(): List<ParameterDefObject> =
        parameters + listOf(ParameterDefObject.LEVEL, ParameterDefObject.ATTACK_RELEASE)

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
            +"~ponticello_addr.sendMsg('/updated', 'synth_def:${name.now}')"
        }
    }

    override fun sync() {
        context[SuperColliderClient].run { sync() }
        Logger.confirm("Synchronized SynthDef '${name.now}'", Logger.Category.Instruments)
    }

    override fun ScWriter.createObject() {
        append("SynthDef(\\${name.now}, ")
        val extraVariables = listOf("duration", "attack", "release", "sustain", "level", "auto_release_", "env_")
        val parameterVariables = parameters.map { p -> Identifier(p.name.now) }
        val parameterAssignments = parameters.map { p ->
            val parameterCode = RawScExpr("\\${p.name.now}.${p.spec.now.code}")
            Assignment(Identifier(p.name.now), parameterCode)
        }
        val variables = ugenGraph?.editor?.result?.now?.variables.orEmpty()
        val extraStatements = listOf(
            RawScExpr("duration = \\duration.ir"),
            RawScExpr("attack = \\attack.kr(${AttackReleaseControl.DEFAULT})"),
            RawScExpr("release = \\release.kr(${AttackReleaseControl.DEFAULT})"),
            RawScExpr("sustain = duration - (attack + release)"),
            RawScExpr("level = \\level.kr(1)"),
            RawScExpr("auto_release_ = \\auto_release.kr(1)"),
            RawScExpr("env_ = Env.asr(attack, 1, release, 'lin').kr(auto_release_ * 2, Env.new([1, 1, 1 - auto_release_], [attack + sustain, 0]).kr * \\gate.kr(1)) * level"),
        )
        val statements = ugenGraph?.editor?.result?.now?.statements.orEmpty()
        val block = CodeBlock(
            variables = extraVariables.map(::Identifier) + parameterVariables + variables,
            statements = extraStatements + parameterAssignments + statements
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
            ParameterDefObject("out", BusControlSpec(Rate.Audio, 2))
        )

        fun lpf() = create(
            name = "lpf",
            ParameterDefObject("bus", BusControlSpec(Rate.Audio, 2))
        )
    }
}