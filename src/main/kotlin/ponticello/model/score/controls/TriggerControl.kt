package ponticello.model.score.controls

import fxutils.drag.TypedDataFormat
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import ponticello.model.instr.ParameterizedObject
import ponticello.model.live.ItemTarget
import ponticello.sc.ControlSpec
import reaktive.event.unitEvent

@Serializable
@SerialName("Trigger")
class TriggerControl : ParameterControl() {
    @Transient
    val trigger = unitEvent()

    override fun copy(): ParameterControl = TriggerControl()

    override fun validate(spec: ControlSpec, obj: ParameterizedObject): Boolean = true

    override fun writeCode(
        parameter: String, spec: ControlSpec?, obj: ParameterizedObject
    ): String = "TriggerControl.new('$parameter')"

    companion object {
        val DATA_FORMAT = TypedDataFormat<ItemTarget.Trigger>("ponticello/trigger")
    }
}