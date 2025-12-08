package ponticello.model.score.controls

import hextant.context.Context
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import ponticello.impl.Logger
import ponticello.impl.copy
import ponticello.model.instr.BusObject
import ponticello.model.instr.ParameterizedObject
import ponticello.model.obj.BusReference
import ponticello.model.registry.reference
import ponticello.model.server.BusRegistry
import ponticello.sc.BusControlSpec
import ponticello.sc.ControlSpec
import reaktive.value.ReactiveVariable
import reaktive.value.now
import reaktive.value.reactiveVariable

@Serializable
@SerialName("Bus")
class BusControl(val bus: ReactiveVariable<BusReference>) : ParameterControl() {
    override fun initialize(context: Context, namedControl: ParameterControlList.NamedParameterControl) {
        super.initialize(context, namedControl)
        bus.now.resolve(context[BusRegistry])
    }

    override fun copy(): ParameterControl = BusControl(bus.copy())

    override fun validate(spec: ControlSpec, obj: ParameterizedObject): Boolean {
        if (spec !is BusControlSpec) {
            Logger.warn("Expected BusControlSpec but got $spec", Logger.Category.Playback)
            return false
        }
        return checkResolution(obj, bus.now, "Bus")
    }

    override fun writeCode(parameter: String, spec: ControlSpec?, obj: ParameterizedObject): String =
        "ValueControl('$parameter', ${bus.now.superColliderName})"

    companion object {
        fun create(bus: BusObject) = BusControl(reactiveVariable(bus.reference()))
    }
}