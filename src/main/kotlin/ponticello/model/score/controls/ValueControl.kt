package ponticello.model.score.controls

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import ponticello.impl.*
import ponticello.model.instr.ParameterizedObject
import ponticello.sc.BufferPositionControlSpec
import ponticello.sc.ControlSpec
import ponticello.sc.NumericalControlSpec
import reaktive.value.ReactiveVariable
import reaktive.value.now
import reaktive.value.reactiveVariable

@Serializable
@SerialName("Value")
data class ValueControl(val value: ReactiveVariable<Decimal>) : ParameterControl() {
    override fun copy(): ParameterControl = ValueControl(value.copy())

    override fun validate(spec: ControlSpec, obj: ParameterizedObject): Boolean {
        if (spec !is NumericalControlSpec) {
            Logger.warn("Expected NumericalControlSpec but got $spec", Logger.Category.Playback)
            return false
        }
        return true
    }

    override fun writeCode(parameter: String, spec: ControlSpec?, obj: ParameterizedObject): String =
        writeCode(group = false) {
        val cutoffMultiplier = cutoffMultiplier(obj, spec)
            append("ValueControl('$parameter', ")
        append(value.now.toString())
            if (spec is NumericalControlSpec && spec.allocateBus) append(", allocate_bus: true")
        if (cutoffMultiplier != zero) append(", cutoff_multiplier: $cutoffMultiplier")
        append(")")
    }

    private fun cutoffMultiplier(obj: ParameterizedObject, spec: ControlSpec?): Decimal =
        if (spec is NumericalControlSpec && spec.origin is BufferPositionControlSpec) {
            val rateCtrl = obj.controls.getOrNull("rate")?.now as? ValueControl
            rateCtrl?.value?.now ?: one
        } else zero

    companion object {
        fun create(value: Decimal) = ValueControl(reactiveVariable(value))
    }
}