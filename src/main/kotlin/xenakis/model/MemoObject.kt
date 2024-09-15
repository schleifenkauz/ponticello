package xenakis.model

import hextant.core.editor.ListenerManager
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import reaktive.value.ReactiveVariable
import reaktive.value.reactiveVariable
import xenakis.ui.MemoObjectView

@Serializable
class MemoObject(
    @SerialName("name") override val mutableName: ReactiveVariable<String>,
    @SerialName("text") private var _text: String,
    @SerialName("width") private var _width: Double
) : ScoreObject() {
    override val type: String
        get() = "memo"

    @Transient
    override val viewManager = ListenerManager.createWeakListenerManager<MemoObjectView>()

    var width: Double
        get() = _width
        set(value) {
            if (_width == value) return
            _width = value
            viewManager.notifyListeners { resized() }
        }

    var text: String
        get() = _text
        set(value) {
            if (value == _text) return
            _text = value
            viewManager.notifyListeners { textChanged(value) }
        }

    override fun doClone(newName: String): ScoreObject = MemoObject(reactiveVariable(newName), text, width)

    override fun writeCode(
        name: String,
        position: ObjectPosition,
        env: ScorePlayEnv
    ): String = ""
}