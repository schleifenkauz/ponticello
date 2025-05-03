package xenakis.model.score.controls

import hextant.context.Context
import javafx.scene.paint.Color
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import reaktive.event.unitEvent
import reaktive.value.ReactiveVariable
import reaktive.value.reactiveVariable
import xenakis.impl.ColorSerializer
import xenakis.model.obj.ParameterizedObject
import xenakis.model.obj.SynthDefObject
import xenakis.model.score.Envelope
import xenakis.sc.*
import xenakis.sc.client.ScWriter

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
        context: CodegenContext,
    ) {
        spec as NumericalControlSpec
        val envelopeCode = points.code(warp = spec.warp)
        val auxiliaryVarName = auxilBusName(uniqueName, parameter)
        when (context) {
            CodegenContext.Synth, CodegenContext.SubArg -> {
                +"$auxiliaryVarName  = Bus.control(s, 1)"
                val auxiliarySynthName = auxilSynthName(uniqueName, parameter)
                val synthName = "${obj.superColliderPrefix}$uniqueName"
                +"$auxiliarySynthName = { $envelopeCode.kr }.play(target: $synthName, outbus: $auxiliaryVarName, fadeTime: 0, addAction: 'addBefore')"
            }

            CodegenContext.Process -> {
                +"$auxiliaryVarName = $envelopeCode"
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