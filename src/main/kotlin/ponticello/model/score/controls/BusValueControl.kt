package ponticello.model.score.controls

import hextant.context.Context
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import ponticello.impl.Logger
import ponticello.impl.copy
import ponticello.model.instr.ParameterizedObject
import ponticello.model.obj.BusReference
import ponticello.model.server.BusRegistry
import ponticello.sc.ControlSpec
import ponticello.sc.NumericalControlSpec
import reaktive.value.ReactiveVariable
import reaktive.value.now
import reaktive.value.reactiveVariable

@Serializable
@SerialName("BusValue")
class BusValueControl(
    val bus: ReactiveVariable<BusReference>,
    val offset: ReactiveVariable<Int> = reactiveVariable(0)
) : ParameterControl() {
    override fun initialize(context: Context, namedControl: ParameterControlList.NamedParameterControl) {
        super.initialize(context, namedControl)
        bus.now.resolve(context[BusRegistry])
    }

    override fun copy(): ParameterControl = BusValueControl(bus.copy())

    override fun validate(spec: ControlSpec, obj: ParameterizedObject): Boolean {
        if (spec !is NumericalControlSpec) {
            Logger.warn("Expected NumericalControlSpec but got $spec", Logger.Category.Playback)
            return false
        }
        return checkResolution(obj, bus.now, "Bus")
    }

    override fun writeCode(parameter: String, spec: ControlSpec?, obj: ParameterizedObject): String =
        "BusControl('$parameter', ${bus.get().superColliderName}, ${offset.now})"
}