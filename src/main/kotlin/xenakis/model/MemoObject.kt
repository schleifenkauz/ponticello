package xenakis.model

import hextant.core.editor.ViewManager
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.put
import xenakis.impl.getDouble
import xenakis.impl.getString
import xenakis.ui.MemoObjectView

class MemoObject(name: String, text: String, var width: Double) : ScoreObject(name) {
    override val type: String
        get() = "memo"

    override val viewManager = ViewManager.createWeakViewManager<MemoObjectView>()

    var text = text
        set(value) {
            if (value == field) return
            field = value
            viewManager.notifyViews { textChanged(value) }
        }

    override fun clone(): ScoreObject = MemoObject(name, text, width)

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