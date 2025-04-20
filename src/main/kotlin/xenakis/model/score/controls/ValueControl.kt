package xenakis.model.score.controls

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import reaktive.value.ReactiveVariable
import reaktive.value.now
import reaktive.value.reactiveVariable
import xenakis.impl.Decimal
import xenakis.impl.Logger
import xenakis.impl.copy
import xenakis.model.obj.ParameterizedObject
import xenakis.sc.*
import xenakis.sc.client.ScWriter

@Serializable
@SerialName("Value")
data class ValueControl(val value: ReactiveVariable<Decimal>) : ParameterControl() {
    override fun copy(): ParameterControl = ValueControl(value.copy())

    override fun validate(spec: ControlSpec, obj: ParameterizedObject): Boolean {
        if (spec !is NumericalControlSpec) {
            Logger.error("Expected NumericalControlSpec but got $spec")
            return false
        }
        return true
    }

    override fun ScWriter.generatePreparationCode(
        obj: ParameterizedObject, uniqueName: String,
        parameter: String, spec: ControlSpec,
        associatedServerObjects: MutableList<String>,
        context: CodegenContext,
    ) {
        val argVar = uniqueArgumentName(uniqueName, parameter)
        when (context) {
            CodegenContext.Process -> +"$argVar = ${value.now}"

            else -> {
                +"$argVar = Bus.control(s, 1)"
                +"$argVar.set(${value.now})"
            }
        }
    }

    override fun ScWriter.applyToSynth(
        obj: ParameterizedObject, synthVar: String,
        parameter: String, spec: ControlSpec,
    ) {
        val busName = uniqueArgumentName(synthVar.removePrefix("~synth_"), parameter)
        +"$synthVar.map('$parameter', $busName)"
    }

    override fun generateArgumentExpr(
        obj: ParameterizedObject, uniqueName: String,
        parameter: String, spec: ControlSpec, context: CodegenContext,
    ): ScExpr = when (context) {
        CodegenContext.Process -> lambda {
            Identifier(uniqueArgumentName(uniqueName, parameter))
        }

        CodegenContext.SubArg -> Identifier(uniqueArgumentName(uniqueName, parameter)).send("kr")
        CodegenContext.Synth -> DecimalLiteral(value.now)
    }

    companion object {
        fun create(value: Decimal) = ValueControl(reactiveVariable(value))
    }
}