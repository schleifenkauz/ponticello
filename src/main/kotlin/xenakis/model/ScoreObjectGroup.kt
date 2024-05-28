package xenakis.model

import hextant.context.Context
import hextant.core.editor.ViewManager
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import xenakis.impl.ScWriter
import xenakis.impl.getSerializableValue
import xenakis.impl.putSerializableValue
import xenakis.ui.ScoreObjectView

class ScoreObjectGroup(name: String, val score: Score) : AbstractScoreObject(name) {
    override val type: String
        get() = "compound"

    override val viewManager = ViewManager.createWeakViewManager<ScoreObjectView>()

    override fun addToScore(score: Score, context: Context) {
        super.addToScore(score, context)
        for (obj in this.score.objects) {
            obj.addToScore(score, context)
        }
    }

    override fun writeStartCode(writer: ScWriter, offset: Double) {
        score.writePlayerTask(writer, offset, taskName = "play_$name")
    }

    override fun writeStopCode(writer: ScWriter) {
        writer.appendLine("play_$name.stop;")
    }

    override fun copy(): ScoreObject = ScoreObjectGroup(name, score)

    override fun JsonObjectBuilder.saveToJson() {
        putSerializableValue("score", score)
    }

    object Serializer : ScoreObject.Serializer {
        override val type: String get() = "compound"

        override fun JsonObject.createFromJson(name: String): ScoreObject {
            val score = getSerializableValue<Score>("score")!!
            return ScoreObjectGroup(name, score)
        }
    }
}