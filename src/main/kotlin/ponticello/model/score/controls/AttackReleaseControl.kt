package ponticello.model.score.controls

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import ponticello.impl.*
import ponticello.model.instr.ParameterizedObject
import ponticello.model.score.Envelope
import ponticello.model.score.Envelope.EnvelopePoint
import ponticello.sc.ControlSpec
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

    override fun writeCode(parameter: String, spec: ControlSpec?, obj: ParameterizedObject): String =
        "ASRControl(${attack.now}, ${release.now})"

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