package ponticello.model.score

import javafx.geometry.HorizontalDirection
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import ponticello.impl.Decimal
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

    override val askBeforeDeleting: Boolean get() = false

    override fun doCut(position: Decimal, whichHalf: HorizontalDirection): ScoreObject? = null

    override fun doClone(): ScoreObject = MemoObject()
}