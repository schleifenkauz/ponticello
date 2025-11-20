package ponticello.model.instr

import ponticello.impl.Decimal
import ponticello.model.player.ActiveObject
import ponticello.model.obj.NamedObject
import ponticello.model.score.controls.ParameterControlList
import ponticello.sc.ControlSpec
import reaktive.value.ReactiveValue
import reaktive.value.now

interface ParameterizedObject : NamedObject {
    val def: InstrumentObject

    val controls: ParameterControlList

    val superColliderPrefix: String

    fun activeObjects(): List<ActiveObject>

    fun duration(): ReactiveValue<Decimal>? = null

    fun getSpec(parameter: String): ControlSpec? =
        def.getSpec(parameter)?.now ?: controls.getOrNull(parameter)?.spec?.now

    fun addControlsForAllObjectParameters() {
        for (param in def.allParameters()) {
            val name = param.name.now
            if (name !in controls.controlMap) {
                controls.addControl(name, param.defaultControl())
            }
        }
    }
}