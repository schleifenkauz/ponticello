package xenakis.model.score.controls

import hextant.context.Context
import kotlinx.serialization.Serializable
import reaktive.value.ReactiveVariable
import reaktive.value.now
import xenakis.impl.copy
import xenakis.model.obj.GlobalPatternReference
import xenakis.model.obj.ParameterizedObject
import xenakis.model.registry.GlobalPatternRegistry
import xenakis.sc.ControlSpec
import xenakis.sc.ScExpr
import xenakis.sc.send

@Serializable
class GlobalPatternControl(val pattern: ReactiveVariable<GlobalPatternReference>) : ParameterControl() {
    override fun copy(): ParameterControl = GlobalPatternControl(pattern.copy())

    override fun validate(spec: ControlSpec, obj: ParameterizedObject): Boolean =
        checkResolution(pattern.now, "Pattern")

    override fun initialize(context: Context) {
        super.initialize(context)
        pattern.now.resolve(context[GlobalPatternRegistry])
    }

    override fun generateArgumentExpr(
        obj: ParameterizedObject, uniqueName: String,
        parameter: String, spec: ControlSpec,
    ): ScExpr = pattern.now.force().superColliderExpr.send("next")
}