package xenakis.model.score.controls

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import reaktive.value.ReactiveVariable
import reaktive.value.now
import xenakis.impl.Decimal
import xenakis.impl.Logger
import xenakis.impl.copy
import xenakis.impl.zero
import xenakis.model.obj.ParameterizedObject
import xenakis.model.score.Envelope
import xenakis.model.score.Envelope.EnvelopePoint
import xenakis.sc.ControlSpec
import xenakis.sc.NumericalControlSpec
import xenakis.sc.ScExpr
import xenakis.sc.client.ScWriter

@Serializable
@SerialName("AttackRelease")
data class AttackReleaseControl(
    val attack: ReactiveVariable<Decimal>,
    val release: ReactiveVariable<Decimal>,
    val level: ReactiveVariable<Decimal>,
) : ParameterControl() {
    override fun copy(): ParameterControl = AttackReleaseControl(attack.copy(), release.copy(), level.copy())

    override fun validate(spec: ControlSpec, obj: ParameterizedObject): Boolean {
        if (obj.duration() == null) {
            Logger.error("AttackReleaseControl only works on fixed duration objects.")
            return false
        }
        val violations = mutableListOf<String>()
        if (attack.now < zero) violations.add("attack")
        if (release.now < zero) violations.add("release")
        if (level.now < zero) violations.add("level")
        return if (violations.isNotEmpty()) {
            Logger.error("Invalid properties in AttackReleaseControl: ${violations.joinToString(", ")} are negative.")
            false
        } else {
            true
        }
    }

    override fun providesConstantSynthArgument(): Boolean = false

    override fun generateArgumentExpr(
        obj: ParameterizedObject,
        uniqueName: String,
        parameter: String,
        spec: ControlSpec,
        context: CodegenContext
    ): ScExpr {
        spec as NumericalControlSpec
        val env = generateEnvelope(obj)
        return env.generateArgumentExpr(obj, uniqueName, parameter, spec, context)
    }

    override fun ScWriter.generatePreparationCode(
        obj: ParameterizedObject, uniqueName: String,
        parameter: String, spec: ControlSpec,
        associatedServerObjects: MutableList<String>,
        context: CodegenContext
    ) {
        val ctrl = generateEnvelope(obj)
        return with(ctrl) { generatePreparationCode(obj, uniqueName, parameter, spec, associatedServerObjects, context) }
    }

    override fun ScWriter.applyToSynth(
        obj: ParameterizedObject,
        synthVar: String,
        parameter: String,
        spec: ControlSpec
    ) {
        val ctrl = generateEnvelope(obj)
        with (ctrl) { applyToSynth(obj, synthVar, parameter, spec) }
    }

    private fun generateEnvelope(obj: ParameterizedObject): EnvelopeControl {
        val totalDuration = obj.duration()!!.now
        val sustain = totalDuration - (attack.now + release.now)
        val env = Envelope(
            mutableListOf(
                EnvelopePoint(zero, zero),
                EnvelopePoint(attack.now, level.now),
                EnvelopePoint(attack.now + sustain, level.now),
                EnvelopePoint(totalDuration, zero),
            )
        )
        val ctrl = EnvelopeControl(env)
        return ctrl
    }
}