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
    @Transient
    val update = unitEvent()

    override fun initialize(context: Context) {
        points.initialize(context)
    }

    override fun copy(): ParameterControl =
        EnvelopeControl(points = points.copy(), displayColor, display)

    override fun validate(spec: ControlSpec, obj: ParameterizedObject): Boolean = spec is NumericalControlSpec

    override fun providesConstantSynthArgument(): Boolean = true

    override fun ScWriter.generatePreparationCode(
        obj: ParameterizedObject, uniqueName: String,
        parameter: String, spec: ControlSpec,
        associatedServerObjects: MutableList<String>,
    ) {
        spec as NumericalControlSpec
        val envelopeCode = points.code(warp = spec.warp)
        val auxiliaryVarName = uniqueArgumentName(uniqueName, parameter)
        when (obj.def) {
            is SynthDefObject -> {
                +"$auxiliaryVarName  = Bus.control(s, 1)"
                val auxiliarySynthName = envSynthName(uniqueName, parameter)
                val synthName = "~synth_$uniqueName"
                +"$auxiliarySynthName = { $envelopeCode.kr }.play($synthName, $auxiliaryVarName, fadeTime: 0, addAction: 'addBefore')"
                associatedServerObjects.addAll(listOf(auxiliarySynthName, auxiliaryVarName))
            }

            is ProcessDefObject -> {
                +"$auxiliaryVarName = $envelopeCode"
            }
        }
    }

    override fun generateSubArgumentExpr(
        obj: ParameterizedObject,
        uniqueName: String,
        parameter: String,
        spec: ControlSpec,
    ): ScExpr = when (obj.def) {
        is SynthDefObject -> Identifier(uniqueArgumentName(uniqueName, parameter)).send("kr")
        else -> generateArgumentExpr(obj, uniqueName, parameter, spec)
    }

    override fun generateArgumentExpr(
        obj: ParameterizedObject, uniqueName: String,
        parameter: String, spec: ControlSpec,
    ): ScExpr {
        spec as NumericalControlSpec
        val auxiliaryVarName = uniqueArgumentName(uniqueName, parameter)
        return when (obj.def) {
            is SynthDefObject -> DecimalLiteral(points.points.first().value)
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
        val auxiliaryVarName = uniqueArgumentName(synthVar.removePrefix("~synth_"), parameter)
        +"${synthVar}.map(\\$parameter, $auxiliaryVarName)"
    }

    companion object {
        fun envSynthName(uniqueName: String, parameter: String) = "~env_synth_${uniqueName}_$parameter"
    }
}