package xenakis.model.score.controls

import hextant.context.Context
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import reaktive.value.ReactiveVariable
import reaktive.value.now
import xenakis.impl.Logger
import xenakis.impl.copy
import xenakis.model.obj.GroupReference
import xenakis.model.obj.ParameterizedObject
import xenakis.model.registry.GroupRegistry
import xenakis.sc.ControlSpec
import xenakis.sc.GroupControlSpec
import xenakis.sc.Identifier
import xenakis.sc.ScExpr

@Serializable
@SerialName("Group")
data class GroupControl(val group: ReactiveVariable<GroupReference>) : ParameterControl() {
    override fun initialize(context: Context) {
        group.now.resolve(context[GroupRegistry])
    }

    override fun copy(): ParameterControl = GroupControl(group.copy())

    override fun validate(spec: ControlSpec, obj: ParameterizedObject): Boolean {
        if (spec !is GroupControlSpec) {
            Logger.error("$this is not valid for a parameter with spec $spec")
            return false
        }
        return checkResolution(group.now, "Group")
    }

    override fun generateArgumentExpr(
        obj: ParameterizedObject, uniqueName: String,
        parameter: String, spec: ControlSpec
    ): ScExpr = Identifier(group.now.superColliderName)
}