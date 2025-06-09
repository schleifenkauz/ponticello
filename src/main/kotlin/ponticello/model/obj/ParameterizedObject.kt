package ponticello.model.obj

import ponticello.impl.Decimal
import ponticello.model.player.ActiveObject
import ponticello.model.registry.NamedObject
import ponticello.model.score.ParameterControlList
import ponticello.sc.ControlSpec
import reaktive.value.ReactiveValue
import reaktive.value.now

interface ParameterizedObject : NamedObject {
    val def: InstrumentObject
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