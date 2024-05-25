package xenakis.model

import hextant.core.editor.ViewManager
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import xenakis.impl.getSerializableValue
import xenakis.impl.putSerializableValue
import xenakis.ui.ScoreObjectView

class CompoundScoreObject(name: String, val score: Score) : AbstractScoreObject(name) {
    override val type: String
        get() = "compound"

    override val viewManager = ViewManager.createWeakViewManager<ScoreObjectView>()

    override fun copy(): ScoreObject = CompoundScoreObject(name, score)

    override fun JsonObjectBuilder.saveToJson() {
        putSerializableValue("score", score)
    }

    object Serializer : ScoreObject.Serializer {
        override val type: String get() = "compound"

        override fun JsonObject.createFromJson(name: String): ScoreObject {
            val score = getSerializableValue<Score>("score")!!
            return CompoundScoreObject(name, score)
        }
    }
}