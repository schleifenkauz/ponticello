package xenakis.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import reaktive.value.ReactiveVariable
import reaktive.value.reactiveVariable
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

    var text: String
        get() = _text
        set(value) {
            if (value == _text) return
            _text = value
            notifyListeners<MemoObjectView> { textChanged(value) }
        }

    override fun doClone(newName: String): ScoreObject = MemoObject(reactiveVariable(newName), text)

    override fun writeCode(name: String, position: ObjectPosition, env: ScorePlayEnv): String = ""
}