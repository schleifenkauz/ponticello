package xenakis.model

import hextant.context.Context
import kotlinx.serialization.Serializable
import reaktive.list.ReactiveList
import reaktive.value.now
import reaktive.value.reactiveVariable
import xenakis.model.SuperColliderObject.LiveCycleType

@Serializable
sealed interface SynthDefObject : ParameterizedObject, InstrumentObject {
    override val superColliderName: String
        get() = "\\${name.now}"
    override val liveCycleType: LiveCycleType
        get() = LiveCycleType.ServerBoot

    val parameters: ReactiveList<ParameterDefObject>

    override fun getParameter(name: String): ParameterDefObject =
        parameters.now.find { it.name.now == name } ?: error("Parameter $name not found in SynthDef '${this.name.now}'")

    fun defaultControls(context: Context): SynthControls {
        val controls = parameters.now.associateTo(mutableMapOf()) { p -> p.name.now to p.defaultControl(context) }
        val group = reactiveVariable(context[GroupRegistry].getDefault().createReference())
        controls["group"] = GroupControl(group)
        return SynthControls(controls)
    }
}