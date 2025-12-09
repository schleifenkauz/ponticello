package ponticello.model.instr

import ponticello.impl.Decimal
import ponticello.model.obj.NamedObject
import ponticello.model.score.controls.ParameterControlList
import ponticello.sc.ControlSpec
import reaktive.Reactive
import reaktive.value.ReactiveValue
import reaktive.value.now
import reaktive.value.reactiveValue

interface ParameterizedObject : NamedObject {
    val def: InstrumentObject

    val instrumentChanged: Reactive get() = reactiveValue(Unit)

    val isCreatedInSuperCollider: Boolean

    val controls: ParameterControlList

    fun soundProcessName(objectName: String): String? = null

    val soundProcessName: String? get() = soundProcessName(name.now)

    fun duration(): ReactiveValue<Decimal>? = null

    fun getSpec(parameter: String): ControlSpec? =
        controls.getOrNull(parameter)?.spec?.now ?: def.getSpec(parameter)?.now

    fun addControlsForAllObjectParameters() {
        for (param in def.allParameters()) {
            val name = param.name.now
            if (name !in controls.controlMap) {
                controls.addControl(name, param.defaultControl())
            }
        }
    }
}