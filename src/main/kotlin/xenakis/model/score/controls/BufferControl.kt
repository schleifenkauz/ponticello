package xenakis.model.score.controls

import hextant.context.Context
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import reaktive.value.ReactiveVariable
import reaktive.value.now
import reaktive.value.reactiveVariable
import xenakis.impl.Logger
import xenakis.impl.copy
import xenakis.model.obj.BufferReference
import xenakis.model.obj.ParameterizedObject
import xenakis.model.registry.BufferRegistry
import xenakis.sc.BufferControlSpec
import xenakis.sc.ControlSpec
import xenakis.sc.ScExpr

@Serializable
@SerialName("Buffer")
data class BufferControl(
    val sample: ReactiveVariable<BufferReference>,
    val display: ReactiveVariable<Boolean> = reactiveVariable(true),
) : ParameterControl() {
    override fun initialize(context: Context) {
        sample.now.resolve(context[BufferRegistry])
    }

    override fun copy(): ParameterControl = BufferControl(sample.copy(), display.copy())

    override fun validate(spec: ControlSpec, obj: ParameterizedObject): Boolean {
        if (spec !is BufferControlSpec) {
            Logger.error("Expected BufferControlSpec but got $spec")
            return false
        }
        return checkResolution(sample.now, "Sample")
    }

    override fun generateArgumentExpr(
        obj: ParameterizedObject, uniqueName: String,
        parameter: String, spec: ControlSpec
    ): ScExpr = sample.now.force().superColliderExpr
}