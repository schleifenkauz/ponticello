package xenakis.model.obj

import reaktive.value.ReactiveValue
import reaktive.value.now
import xenakis.impl.Decimal
import xenakis.model.player.ActiveObject
import xenakis.model.registry.NamedObject
import xenakis.model.score.ParameterControlList
import xenakis.sc.ControlSpec

interface ParameterizedObject : NamedObject {
    val def: ParameterizedObjectDef
    val controls: ParameterControlList

    val superColliderPrefix: String

    fun activeObjects(): List<ActiveObject>

    fun duration(): ReactiveValue<Decimal>? = null

    fun getSpec(parameter: String): ControlSpec? = controls.getOrNull(parameter)?.spec?.now

    fun addControlsForAllObjectParameters() {
        for (param in def.allParameters()) {
            val name = param.name.now
            if (name !in controls.controlMap) {
                controls.addControl(name, param.defaultControl())
            }
        }
    }
}