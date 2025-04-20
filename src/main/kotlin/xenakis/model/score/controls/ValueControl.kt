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
import xenakis.model.obj.ProcessDefObject
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
    ) {
        if (obj.def is ProcessDefObject) {
            +"${uniqueArgumentName(uniqueName, parameter)} = ${value.now}"
        }
    }

    override fun generateArgumentExpr(
        obj: ParameterizedObject, uniqueName: String,
        parameter: String, spec: ControlSpec,
    ): ScExpr = when (obj.def) {
        is ProcessDefObject -> Identifier(uniqueArgumentName(uniqueName, parameter))
        else -> DecimalLiteral(value.now)
    }

    companion object {
        fun create(value: Decimal) = ValueControl(reactiveVariable(value))
    }
}