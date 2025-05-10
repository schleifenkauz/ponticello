package ponticello.model.score.controls

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import reaktive.value.ReactiveVariable
import reaktive.value.now
import reaktive.value.reactiveVariable
import ponticello.impl.Decimal
import ponticello.impl.Logger
import ponticello.impl.copy
import ponticello.model.obj.ParameterizedObject
import ponticello.model.obj.SynthDefObject
import ponticello.sc.*
import ponticello.sc.client.ScWriter

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

    override fun providesConstantSynthArgument(): Boolean = !allocateBus.now

    override fun usesAuxilSynth(obj: ParameterizedObject): Boolean = false

    override fun ScWriter.generatePreparationCode(
        obj: ParameterizedObject, uniqueName: String,
        parameter: String, spec: ControlSpec,
        ctx: CodegenContext,
    ) {
        when {
            ctx == CodegenContext.Process -> {
                val argVar = uniqueArgumentName(uniqueName, parameter)
                +"$argVar = ${value.now}"
            }

            allocateBus.now -> {
                val busName = auxilBusName(uniqueName, parameter)
                +"$busName = Bus.control(s, 1)"
                +"$busName.set(${value.now})"
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
        parameter: String, spec: ControlSpec, context: CodegenContext,
    ): ScExpr = when (context) {
        CodegenContext.Process -> lambda {
            Identifier(uniqueArgumentName(uniqueName, parameter))
        }

        CodegenContext.SubArg ->
            if (allocateBus.now) Identifier(auxilBusName(uniqueName, parameter)).send("kr")
            else DecimalLiteral(value.now)

        CodegenContext.Synth -> DecimalLiteral(value.now)
    }

    companion object {
        fun create(value: Decimal) = ValueControl(reactiveVariable(value))
    }
}