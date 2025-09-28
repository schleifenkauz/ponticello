package ponticello.model.score

import javafx.geometry.HorizontalDirection
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import ponticello.impl.Decimal
import ponticello.model.flow.NodePlacement
import ponticello.model.obj.ParameterDefObject
import ponticello.model.score.controls.ParameterControl
import reaktive.value.ReactiveVariable

@Serializable
@SerialName("Memo")
class MemoObject : ScoreObject() {
    @SerialName("name")
    override var _name: ReactiveVariable<String>? = null

    override val type: String
        get() = "memo"

    override val canMute: Boolean
        get() = false

    override val canResizeHorizontally: Boolean
        get() = false

    override val canResizeVertically: Boolean
        get() = false

    override val affectsPlayback: Boolean
        get() = false

    override fun doCut(position: Decimal, whichHalf: HorizontalDirection): ScoreObject? = null

    override fun doClone(): ScoreObject = MemoObject()

    override fun writeCode(
        instance: ScoreObjectInstance?,
        uniqueName: String,
        placement: NodePlacement?,
        cutoff: Decimal,
        latency: Decimal,
        extraArguments: Map<ParameterDefObject, ParameterControl>
    ): String = ""
}