package xenakis.model.score.controls

import hextant.context.Context
import javafx.scene.paint.Color
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import reaktive.value.ReactiveVariable
import reaktive.value.reactiveVariable
import xenakis.impl.ColorSerializer
import xenakis.model.obj.ParameterizedObject
import xenakis.model.obj.ProcessDefObject
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
    override fun initialize(context: Context) {
        points.initialize(context)
    }

    override fun copy(): ParameterControl =
        EnvelopeControl(points = points.copy(), displayColor, display)

    override fun validate(spec: ControlSpec, obj: ParameterizedObject): Boolean = spec is NumericalControlSpec

    override fun providesConstantSynthArgument(): Boolean = false

    override fun ScWriter.generatePreparationCode(
        obj: ParameterizedObject, uniqueName: String,
        parameter: String, spec: ControlSpec,
        associatedServerObjects: MutableList<String>,
    ) {
        spec as NumericalControlSpec
        val envelopeCode = points.code(warp = spec.warp)
        val auxiliaryVarName = getAuxiliaryVarName(uniqueName, parameter)
        when (obj.def) {
            is SynthDefObject -> {
                +"$auxiliaryVarName  = Bus.control(s, 1)"
                val auxiliarySynthName = "~auxil_synth_${uniqueName}_$parameter"
                +"$auxiliarySynthName = { $envelopeCode.kr }.play(s, $auxiliaryVarName)"
                associatedServerObjects.addAll(listOf(auxiliarySynthName, auxiliaryVarName))
            }

            is ProcessDefObject -> {
                +"$auxiliaryVarName = $envelopeCode"
            }
        }
    }

    override fun generateArgumentExpr(
        obj: ParameterizedObject, uniqueName: String,
        parameter: String, spec: ControlSpec,
    ): ScExpr {
        spec as NumericalControlSpec
        val auxiliaryVarName = getAuxiliaryVarName(uniqueName, parameter)
        return when (obj.def) {
            is SynthDefObject -> Identifier(auxiliaryVarName).send("kr")
            is ProcessDefObject -> lambda("t") { Identifier(auxiliaryVarName).send("at", Identifier("t")) }
            else -> RawScExpr(points.code(warp = spec.warp))
        }
    }

    override fun ScWriter.applyToSynth(
        obj: ParameterizedObject,
        synthVar: String,
        parameter: String,
        spec: ControlSpec,
    ) {
        val auxiliaryVarName = getAuxiliaryVarName(synthVar, parameter)
        +"${synthVar}.map(\\$parameter, $auxiliaryVarName)"
    }

    private fun getAuxiliaryVarName(uniqueName: String, parameter: String) = "~auxil_${uniqueName}_${parameter}"
}