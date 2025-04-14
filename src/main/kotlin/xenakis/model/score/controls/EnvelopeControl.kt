package xenakis.model.score.controls

import hextant.context.Context
import javafx.scene.paint.Color
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
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
    override fun initialize(context: Context) {
        points.initialize(context)
    }

    override fun copy(): ParameterControl =
        EnvelopeControl(points = points.copy(), displayColor, display)

    override fun validate(spec: ControlSpec, obj: ParameterizedObject): Boolean = spec is NumericalControlSpec

    override fun providesConstantSynthArgument(): Boolean = false

    override fun ScWriter.applyToSynth(
        parameter: String, spec: ControlSpec,
        obj: ParameterizedObject, synthVar: String,
    ) {
        spec as NumericalControlSpec
        val envelopeCode = points.code(warp = spec.warp)
        val busName = "~auxil_${synthVar.removePrefix("~synth_")}_${parameter}"
        +"$busName  = Bus.control(s, 1)"
        +"{ $envelopeCode.kr }.play(s, $busName)" //TODO free up when pausing the other parent object!
        +"${synthVar}.map(\\$parameter, $busName)"
    }

    override fun generateCodeFor(obj: ParameterizedObject, spec: ControlSpec): ScExpr {
        spec as NumericalControlSpec
        val envCode = RawScExpr(points.code(warp = spec.warp))
        return when (obj.def) {
            is SynthDefObject -> envCode.send("kr")
            else -> envCode
        }
    }
}