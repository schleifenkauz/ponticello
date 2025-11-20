package ponticello.model.score.controls

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import ponticello.impl.*
import ponticello.model.instr.ParameterizedObject
import ponticello.model.instr.SynthDefObject
import ponticello.model.score.Envelope
import ponticello.model.score.Envelope.EnvelopePoint
import ponticello.sc.ControlSpec
import ponticello.sc.NumericalControlSpec
import ponticello.sc.ScExpr
import reaktive.value.ReactiveVariable
import reaktive.value.now
import reaktive.value.reactiveVariable

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

    override fun providesConstantSynthArgument(obj: ParameterizedObject, spec: ControlSpec, cutoff: Decimal): Boolean = false

    override fun customSynthArguments(cutoff: Decimal, totalDuration: Decimal): String {
        val attack = (attack.now - cutoff).coerceAtLeast(zero)
        val remainingDur = totalDuration - cutoff
        check(remainingDur > zero) { "Negative remaining duration: $totalDuration - $cutoff = $remainingDur" }
        val release = (release.now).coerceAtMost(remainingDur)
        return "attack: ${attack}, release: ${release}, "
    }

    override fun allocatesBus(obj: ParameterizedObject, spec: ControlSpec?): Boolean = obj.def is SynthDefObject

    override fun usesAuxilSynth(obj: ParameterizedObject, spec: ControlSpec?): Boolean = obj.def is SynthDefObject

    override fun generateArgumentExpr(
        obj: ParameterizedObject,
        uniqueName: String,
        parameter: String,
        spec: ControlSpec,
        cutoff: Decimal,
        context: CodegenContext,
    ): ScExpr {
        spec as NumericalControlSpec
        val env = generateEnvelope(obj)
        return env.generateArgumentExpr(obj, uniqueName, parameter, spec, cutoff, context)
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