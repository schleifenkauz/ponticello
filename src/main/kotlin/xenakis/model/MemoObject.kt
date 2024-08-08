package xenakis.model

import hextant.core.editor.ListenerManager
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.put
import reaktive.value.now
import xenakis.impl.getDouble
import xenakis.impl.getString
import xenakis.ui.MemoObjectView

class MemoObject(name: String, text: String, width: Double) : RegularScoreObject(name) {
    override val type: String
        get() = "memo"

    override val viewManager = ListenerManager.createWeakListenerManager<MemoObjectView>()

    var width = width
        set(value) {
            if (field == value) return
            field = value
            viewManager.notifyListeners { resized() }
        }

    var text = text
        set(value) {
            if (value == field) return
            field = value
            viewManager.notifyListeners { textChanged(value) }
        }

    override fun copy(): ScoreObject = MemoObject(name.now, text, width)

    override fun writeCode(env: ScorePlayEnv, name: String, cutoff: Double): String = ""

    override fun JsonObjectBuilder.saveToJson() {
        put("text", text)
        put("width", width)
    }

    object Serializer : ScoreObject.Serializer {
        override val type: String
            get() = "memo"

        override fun JsonObject.createFromJson(name: String): ScoreObject {
            val text = getString("text") ?: ""
            val width = getDouble("width") ?: 0.0
            return MemoObject(name, text, width)
        }
    }
}