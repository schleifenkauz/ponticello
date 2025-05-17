package ponticello.model.score.controls

import hextant.context.Context
import javafx.scene.paint.Color
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import ponticello.impl.ColorSerializer
import ponticello.impl.Decimal
import ponticello.model.obj.ParameterizedObject
import ponticello.model.obj.SynthDefObject
import ponticello.model.score.Envelope
import ponticello.sc.*
import ponticello.sc.client.ScWriter
import reaktive.event.unitEvent
import reaktive.value.ReactiveVariable
import reaktive.value.reactiveVariable

@Serializable
@SerialName("Envelope")
class EnvelopeControl(
    val points: Envelope,
    val displayColor: ReactiveVariable<@Serializable(with = ColorSerializer::class) Color> = reactiveVariable(Color.BLACK),
    val display: ReactiveVariable<Boolean> = reactiveVariable(true),
) : ParameterControl() {
    @Transient
    val update = unitEvent()

    override fun initialize(context: Context, parentObject: ParameterizedObject) {
        super.initialize(context, parentObject)
        points.initialize(context)
    }

    override fun copy(): ParameterControl =
        EnvelopeControl(points = points.copy(), displayColor, display)

    override fun validate(spec: ControlSpec, obj: ParameterizedObject): Boolean = spec is NumericalControlSpec

    override fun providesConstantSynthArgument(): Boolean = true

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
                val envelopeCode = points.generatorCode(spec.warp, cutoff)
//                val synthDefName = context[AuxilSynthDefManager].run {
//                    defineEnvelopeSynthDef(points, spec.warp)
//                }
                val auxiliarySynthName = auxilSynthName(uniqueName, parameter)
                val synthName = "${obj.superColliderPrefix}$uniqueName"
                +"$auxiliarySynthName = { $envelopeCode }.play(target: $synthName, outbus: $auxiliaryVarName, fadeTime: 0, addAction: 'addBefore')"
//                +"$auxiliarySynthName = Synth.newPaused($synthDefName, [out: $auxiliarySynthName], target: $synthName, addAction: \\addBefore)"
            }

            CodegenContext.Process -> {
                +"$auxiliaryVarName = ${points.code(spec.warp)}"
            }
        }
    }

    override fun generateArgumentExpr(
        obj: ParameterizedObject, uniqueName: String,
        parameter: String, spec: ControlSpec, context: CodegenContext,
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
}