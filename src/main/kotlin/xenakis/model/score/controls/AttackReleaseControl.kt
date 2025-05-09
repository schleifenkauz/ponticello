package xenakis.model.score.controls

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import reaktive.value.ReactiveVariable
import reaktive.value.now
import reaktive.value.reactiveVariable
import xenakis.impl.*
import xenakis.model.obj.ParameterizedObject
import xenakis.model.obj.SynthDefObject
import xenakis.model.score.Envelope
import xenakis.model.score.Envelope.EnvelopePoint
import xenakis.sc.ControlSpec
import xenakis.sc.NumericalControlSpec
import xenakis.sc.ScExpr

@Serializable
@SerialName("AttackRelease")
data class AttackReleaseControl(
    val attack: ReactiveVariable<Decimal>,
    val release: ReactiveVariable<Decimal>,
) : ParameterControl() {
    override fun copy(): ParameterControl = AttackReleaseControl(attack.copy(), release.copy())

    override fun validate(spec: ControlSpec, obj: ParameterizedObject): Boolean {
        val violations = mutableListOf<String>()
        if (attack.now < zero) violations.add("attack")
        if (release.now < zero) violations.add("release")
        return if (violations.isNotEmpty()) {
            Logger.error("Invalid properties in AttackReleaseControl: ${violations.joinToString(", ")} are negative.")
            false
        } else {
            true
        }
    }

    override fun providesConstantSynthArgument(): Boolean = false

    override fun customSynthArguments(): String = "attack: ${attack.now}, release: ${release.now}, "

    override fun allocatesBus(obj: ParameterizedObject): Boolean = obj.def is SynthDefObject

    override fun usesAuxilSynth(obj: ParameterizedObject): Boolean = obj.def is SynthDefObject

    override fun generateArgumentExpr(
        obj: ParameterizedObject,
        uniqueName: String,
        parameter: String,
        spec: ControlSpec,
        context: CodegenContext,
    ): ScExpr {
        spec as NumericalControlSpec
        val env = generateEnvelope(obj)
        return env.generateArgumentExpr(obj, uniqueName, parameter, spec, context)
    }

    private fun generateEnvelope(obj: ParameterizedObject): EnvelopeControl {
        val totalDuration = obj.duration()!!.now
        val sustain = totalDuration - (attack.now + release.now)
        val env = Envelope(
            mutableListOf(
                EnvelopePoint(zero, zero),
                EnvelopePoint(attack.now, one),
                EnvelopePoint(attack.now + sustain, one),
                EnvelopePoint(totalDuration, zero),
            )
        )
        return EnvelopeControl(env)
    }

    companion object {
        val DEFAULT = 0.02.toDecimal()

        fun createDefault() = AttackReleaseControl(reactiveVariable(DEFAULT), reactiveVariable(DEFAULT))
    }
}