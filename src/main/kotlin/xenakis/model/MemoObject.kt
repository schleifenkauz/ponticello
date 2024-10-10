package xenakis.model

import javafx.geometry.HorizontalDirection
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import reaktive.value.ReactiveVariable
import reaktive.value.reactiveVariable
import xenakis.impl.Decimal
import xenakis.ui.MemoObjectView

@Serializable
class MemoObject(
    @SerialName("name") override val mutableName: ReactiveVariable<String>,
    @SerialName("text") private var _text: String,
) : ScoreObject() {
    override val type: String
        get() = "memo"

    override val canMute: Boolean
        get() = false

    override val canResize: Boolean
        get() = false

    var text: String
        get() = _text
        set(value) {
            if (value == _text) return
            _text = value
            notifyListeners<MemoObjectView> { textChanged(value) }
        }

    override fun doCut(position: Decimal, whichHalf: HorizontalDirection, newName: String): ScoreObject? = null

    override fun doClone(newName: String): ScoreObject = MemoObject(reactiveVariable(newName), text)

    override fun writeCode(name: String, position: ObjectPosition, env: ScorePlayEnv): String = ""
}