package xenakis.model.obj

import hextant.context.Context
import reaktive.list.ReactiveList
import reaktive.value.now
import xenakis.model.registry.ObjectReference
import xenakis.model.score.ParameterControls

interface ParameterizedObjectDef {
    val parameters: ReactiveList<ParameterDefObject>

    fun getParameter(name: String): ParameterDefObject? = parameters.now.find { it.name.now == name }

    fun hasParameter(name: String): Boolean = parameters.now.any { it.name.now == name }

    fun defaultControls(
        context: Context, defaultGroup: GroupReference?, defaultBus: BusReference?
    ): ParameterControls {
        val controls =
            parameters.now.associateTo(mutableMapOf()) { p ->
                p.name.now to p.defaultControl(context, defaultBus)
            }
        return ParameterControls(controls)
    }
}