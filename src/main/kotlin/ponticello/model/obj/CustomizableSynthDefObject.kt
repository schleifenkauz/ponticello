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
import kotlinx.serialization.Serializable
import ponticello.impl.ColorSerializer
import ponticello.impl.Logger
import ponticello.impl.copy
import ponticello.impl.randomColor
import ponticello.model.registry.InstrumentRegistry
import ponticello.model.score.controls.AttackReleaseControl
import ponticello.sc.*
import ponticello.sc.client.ScWriter
import ponticello.sc.client.SuperColliderClient
import ponticello.sc.client.run
import ponticello.sc.editor.CodeBlockEditor
import ponticello.ui.registry.ParameterDefList
import reaktive.value.ReactiveVariable
import reaktive.value.now
import reaktive.value.reactiveVariable

@Serializable
class CustomizableSynthDefObject(
    override val parameters: ParameterDefList,
    override val color: ReactiveVariable<@Serializable(with = ColorSerializer::class) Color> = reactiveVariable(Color.WHITE),
    val ugenGraph: EditorRoot<@Contextual CodeBlockEditor>? = null,
) : SynthDefObject, AbstractRenamableObject(), ConfigurableInstrumentObject {
    override val canCopy: Boolean
        get() = true

    override fun supports(type: ParameterType): Boolean = type != ParameterType.Expr

    override fun allParameters(): List<ParameterDefObject> {
        val attackRelease = parameters.any {
            val spec = it.spec.now
            spec is NumericalControlSpec && spec.attackRelease
        }
        val additionalParameters = if (attackRelease) listOf(ParameterDefObject.ATTACK_RELEASE) else emptyList()
        return parameters + additionalParameters
    }

    override fun hasParameter(name: String): Boolean = when (name) {
        "attack", "release" -> parameters.any { p ->
            val spec = p.spec.now
            spec is NumericalControlSpec && spec.attackRelease
        }
        else -> super<ConfigurableInstrumentObject>.hasParameter(name)
    }

    override fun copy(): CustomizableSynthDefObject = CustomizableSynthDefObject(
        ParameterDefList(parameters.mapTo(mutableListOf()) { p -> p.copy().withName(p.name.now) }),
        color.copy(),
        ugenGraph?.clone()
    )

    override fun ScWriter.sync() {
        appendBlock("fork") {
            createObject()
            +"s.sync"
            +"~ponticello_addr.sendMsg('/updated', 'synth_def', '${name.now}')"
        }
    }

    override fun sync() {
        context[SuperColliderClient].run { sync() }
        Logger.confirm("Synchronized SynthDef '${name.now}'", Logger.Category.Instruments)
    }

    override fun ScWriter.createObject() {
        append("SynthDef(\\${name.now}, ")
        val extraVariables = mutableListOf("duration", "gate_env_")
        val parameterVariables = parameters.map { p -> Identifier(p.name.now) }
        val parameterAssignments = parameters.map { p ->
            val parameterCode = RawScExpr("\\${p.name.now}.${p.spec.now.code}")
            Assignment(Identifier(p.name.now), parameterCode)
        }
        val variables = ugenGraph?.editor?.result?.now?.variables.orEmpty()
        val extraStatements = mutableListOf<String>()
        val attackReleaseParameters = parameters.associate { p -> p.name.now to p.spec.now }.filter { (_, spec) ->
            spec is NumericalControlSpec && spec.attackRelease
        }
        extraStatements.add("duration = \\duration.ir")
        if (attackReleaseParameters.isNotEmpty()) {
            extraVariables.addAll(listOf("attack", "release", "sustain_", "env_"))
            extraStatements.add("attack = \\attack.kr(${AttackReleaseControl.DEFAULT})")
            extraStatements.add("release = \\release.kr(${AttackReleaseControl.DEFAULT})")
            extraStatements.add("sustain_ = duration - (attack + release)")
            extraStatements.add(
                "gate_env_ = IEnvGen.kr(" +
                        "Env.new([1, 1, 1 - \\auto_release.kr(1)], [attack + sustain_, 0]), " +
                        "index: Sweep.kr(rate: ~time_warp_bus.kr))"
            )
            extraStatements.add(
                "env_ = Env.asr(attack, 1, release, curve: 2)" +
                        ".kr(Done.freeSelf, gate: gate_env_ * \\gate.kr(1), timeScale: ~time_warp_bus.kr)"
            )
            for ((param, spec) in attackReleaseParameters) {
                spec as NumericalControlSpec
                extraStatements.add("$param = env_.${spec.warp.mappingFunction(spec.min.text, param)}")
            }
        } else {
            extraStatements.add(
                "gate_env_ = IEnvGen.kr(" +
                        "Env.step([0, \\auto_release.kr(1)], [duration, 1]), " +
                        "index: Sweep.kr(rate: ~time_warp_bus.kr))"
            )
            extraStatements.add("FreeSelf.kr(gate_env_)")
        }
        val statements = ugenGraph?.editor?.result?.now?.statements.orEmpty()
        val block = CodeBlock(
            variables = extraVariables.map(::Identifier) + parameterVariables + variables,
            statements = parameterAssignments + extraStatements.map(::RawScExpr) + statements
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

    override fun canRenameTo(newName: String): Boolean = !context[InstrumentRegistry].has(newName)

    override fun rename(newName: String) {
        onRemoved()
        super.rename(newName)
        context[SuperColliderClient].run { this.sync() }
    }

    companion object {
        val editedSynthDef = publicProperty<CustomizableSynthDefObject>("edited synth def")

        fun create(name: String) = CustomizableSynthDefObject(
            color = reactiveVariable(randomColor()),
            parameters = ParameterDefList(),
            ugenGraph = EditorRoot(CodeBlockEditor().defaultState())
        ).withName(name)

        fun create(name: String, vararg parameters: ParameterDefObject) =
            CustomizableSynthDefObject(ParameterDefList(parameters.toMutableList())).withName(name)

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