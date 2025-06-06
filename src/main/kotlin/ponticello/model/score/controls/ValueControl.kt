package ponticello.model.score.controls

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import ponticello.impl.Decimal
import ponticello.impl.Logger
import ponticello.impl.copy
import ponticello.impl.one
import ponticello.model.obj.ParameterizedObject
import ponticello.model.obj.SynthDefObject
import ponticello.sc.*
import ponticello.sc.client.ScWriter
import reaktive.value.ReactiveVariable
import reaktive.value.now
import reaktive.value.reactiveVariable

@Serializable
@SerialName("Value")
data class ValueControl(
    val value: ReactiveVariable<Decimal>,
    val allocateBus: ReactiveVariable<Boolean> = reactiveVariable(false),
) : ParameterControl() {
    override fun copy(): ParameterControl = ValueControl(value.copy())

    override fun validate(spec: ControlSpec, obj: ParameterizedObject): Boolean {
        if (spec !is NumericalControlSpec) {
            Logger.error("Expected NumericalControlSpec but got $spec")
            return false
        }
        return true
    }

    override fun allocatesBus(obj: ParameterizedObject): Boolean = allocateBus.now && obj.def is SynthDefObject

    override fun providesConstantSynthArgument(spec: ControlSpec): Boolean =
        !allocateBus.now && spec is NumericalControlSpec && spec.defaultValue.get() != value.now

    override fun usesAuxilSynth(obj: ParameterizedObject): Boolean = false

    override fun ScWriter.generatePreparationCode(
        obj: ParameterizedObject, uniqueName: String,
        parameter: String, spec: ControlSpec, cutoff: Decimal,
        ctx: CodegenContext,
    ) {
        val adjustedValue = adjustValueIfIsBufferPosition(obj, spec, cutoff)
        when {
            ctx == CodegenContext.Process -> {
                val argVar = uniqueArgumentName(uniqueName, parameter)
                +"$argVar = $adjustedValue"
            }

            allocateBus.now -> {
                val busName = auxilBusName(uniqueName, parameter)
                +"$busName = Bus.control(s, 1)"
                +"$busName.set(${adjustedValue})"
            }
        }
    }

    override fun ScWriter.applyToSynth(
        obj: ParameterizedObject, uniqueName: String,
        synthVar: String, parameter: String, spec: ControlSpec,
    ) {
        if (allocateBus.now) {
            val busName = auxilBusName(uniqueName, parameter)
            +"$synthVar.map('$parameter', $busName)"
        }
    }

    override fun generateArgumentExpr(
        obj: ParameterizedObject, uniqueName: String,
        parameter: String, spec: ControlSpec, cutoff: Decimal, context: CodegenContext,
    ): ScExpr {
        val adjustedValue = adjustValueIfIsBufferPosition(obj, spec, cutoff)
        return when (context) {
            CodegenContext.Process -> lambda {
                Identifier(uniqueArgumentName(uniqueName, parameter))
            }

            CodegenContext.SubArg ->
                if (allocateBus.now) Identifier(auxilBusName(uniqueName, parameter)).send("kr")
                else DecimalLiteral(adjustedValue)

            CodegenContext.Synth -> DecimalLiteral(adjustedValue)
        }
    }

    private fun adjustValueIfIsBufferPosition(obj: ParameterizedObject, spec: ControlSpec, cutoff: Decimal) =
         if (spec is NumericalControlSpec && spec.origin is BufferPositionControlSpec) {
            val rateCtrl = obj.controls.getOrNull("rate")?.now as? ValueControl
            val rate = rateCtrl?.value?.now ?: one
            value.now + cutoff * rate
        } else value.now

    companion object {
        fun create(value: Decimal) = ValueControl(reactiveVariable(value))
    }
}