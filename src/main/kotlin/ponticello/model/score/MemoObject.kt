package ponticello.model.score

import javafx.geometry.HorizontalDirection
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import ponticello.impl.Decimal
import ponticello.model.flow.NodePlacement
import ponticello.model.obj.ParameterDefObject
import ponticello.model.score.controls.ParameterControl
import ponticello.ui.score.MemoObjectView

@Serializable
@SerialName("Memo")
class MemoObject(
    @SerialName("text") private var _text: String,
) : ScoreObject() {
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

    var text: String
        get() = _text
        set(value) {
            if (value == _text) return
            _text = value
            notifyListeners<MemoObjectView> { textChanged(value) }
        }

    override fun doCut(position: Decimal, whichHalf: HorizontalDirection): ScoreObject? = null

    override fun doClone(): ScoreObject = MemoObject(text)

    override fun writeCode(
        uniqueName: String,
        placement: NodePlacement?,
        cutoff: Decimal,
        latency: Decimal,
        extraArguments: Map<ParameterDefObject, ParameterControl>
    ): String = ""
}