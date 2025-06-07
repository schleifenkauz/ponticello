package ponticello.model.score.controls

import hextant.context.Context
import javafx.scene.paint.Color
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import ponticello.impl.ColorSerializer
import ponticello.impl.Decimal
import ponticello.impl.Logger
import ponticello.model.flow.NodePlacement
import ponticello.model.obj.ParameterizedObject
import ponticello.model.obj.SynthDefObject
import ponticello.model.score.Envelope
import ponticello.model.score.ParameterControlList.NamedParameterControl
import ponticello.sc.*
import ponticello.sc.client.ScWriter
import ponticello.sc.client.SuperColliderClient
import ponticello.sc.client.run
import ponticello.ui.score.EnvelopeView
import reaktive.Observer
import reaktive.event.unitEvent
import reaktive.value.ReactiveVariable
import reaktive.value.forEach
import reaktive.value.reactiveVariable

@Serializable
@SerialName("Envelope")
class EnvelopeControl(
    val points: Envelope,
    val displayColor: ReactiveVariable<@Serializable(with = ColorSerializer::class) Color> = reactiveVariable(Color.BLACK),
    val display: ReactiveVariable<Boolean> = reactiveVariable(true),
) : ParameterControl(), EnvelopeView {
    @Transient
    private var index = -1

    @Transient
    private lateinit var specObserver: Observer

    @Transient
    private var defaultWarp: Warp? = null

    private val auxilSynthDefName get() = "\\env_$index"

    @Transient
    val update = unitEvent()

    override fun initialize(context: Context, namedControl: NamedParameterControl) {
        index = counter++
        super.initialize(context, namedControl)
        points.initialize(context)
        specObserver = namedControl.spec.forEach { spec ->
            if (spec !is NumericalControlSpec) {
                Logger.error("Expected NumericalControlSpec but got $spec")
            } else if (defaultWarp != spec.warp) {
                defaultWarp = spec.warp
                updateSynthDef()
            }
        }
        points.addListener(this)
    }

    private fun updateSynthDef() {
        context[SuperColliderClient].run {
            appendBlock("SynthDef($auxilSynthDefName)", endLine = false) {
                +"arg out, cutoff = 0"
                +"var env = ${points.code(defaultWarp ?: Warp.Linear)}, sig"
                +"sig = IEnvGen.kr(env, index: Sweep.kr(rate: ~time_warp_bus.kr) + cutoff)"
                +"Out.kr(out, sig)"
            }
            appendLine(".add;")
        }
    }

    override fun copy(): ParameterControl =
        EnvelopeControl(points = points.copy(), displayColor, display)

    override fun validate(spec: ControlSpec, obj: ParameterizedObject): Boolean = spec is NumericalControlSpec

    override fun providesConstantSynthArgument(
        obj: ParameterizedObject, spec: ControlSpec, cutoff: Decimal,
    ): Boolean = true

    override fun allocatesBus(obj: ParameterizedObject): Boolean = obj.def is SynthDefObject

    override fun usesAuxilSynth(obj: ParameterizedObject): Boolean = obj.def is SynthDefObject

    override fun ScWriter.generatePreparationCode(
        obj: ParameterizedObject, uniqueName: String,
        parameter: String, spec: ControlSpec,
        cutoff: Decimal,
        ctx: CodegenContext,
    ) {
        spec as NumericalControlSpec
        val auxiliaryVarName = auxilBusName(uniqueName, parameter)
        when (ctx) {
            CodegenContext.Synth, CodegenContext.SubArg -> {
                +"$auxiliaryVarName = Bus.control(s, 1)"
                val synthName = "${obj.superColliderPrefix}$uniqueName"
                val placement = NodePlacement.before(synthName)
                createEnvelopeSynth(
                    this,
                    auxiliaryVarName,
                    auxilSynthName(uniqueName, parameter),
                    placement,
                    cutoff,
                    paused = true
                )
            }

            CodegenContext.Process -> {
                +"$auxiliaryVarName = ${points.code(spec.warp)}"
            }
        }
    }

    fun createEnvelopeSynth(
        writer: ScWriter,
        auxiliaryVarName: String,
        auxiliarySynthName: String,
        placement: NodePlacement,
        cutoff: Decimal,
        paused: Boolean,
    ) = with(writer) {
        val method = if (paused) "newPaused" else "new"
        +"$auxiliarySynthName = Synth.$method($auxilSynthDefName, [out: $auxiliaryVarName, cutoff: $cutoff], ${placement.code})"
    }

    override fun generateArgumentExpr(
        obj: ParameterizedObject, uniqueName: String,
        parameter: String, spec: ControlSpec, cutoff: Decimal, context: CodegenContext,
    ): ScExpr {
        spec as NumericalControlSpec
        return when (context) {
            CodegenContext.Synth -> DecimalLiteral(points.points.first().value)
            CodegenContext.Process -> lambda("t") {
                val argName = auxilBusName(uniqueName, parameter)
                Identifier(argName).send("at", Identifier("t"))
            }

            else -> Identifier(auxilBusName(uniqueName, parameter)).send("kr")
        }
    }

    override fun ScWriter.applyToSynth(
        obj: ParameterizedObject,
        uniqueName: String,
        synthVar: String,
        parameter: String,
        spec: ControlSpec,
    ) {
        val auxiliaryVarName = auxilBusName(uniqueName, parameter)
        +"${synthVar}.map(\\$parameter, $auxiliaryVarName)"
    }

    override fun addedPoint(idx: Int, point: Envelope.EnvelopePoint) {
        updateSynthDef()
        update.fire()
    }

    override fun removedPoint(idx: Int, point: Envelope.EnvelopePoint) {
        updateSynthDef()
        update.fire()
    }

    override fun changedPoint(idx: Int, newPoint: Envelope.EnvelopePoint) {
        updateSynthDef()
        update.fire()
    }

    companion object {
        private var counter = 0
    }
}