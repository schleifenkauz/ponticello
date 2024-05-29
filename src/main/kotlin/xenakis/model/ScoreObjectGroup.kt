package xenakis.model

import hextant.context.Context
import hextant.core.editor.ViewManager
import javafx.geometry.HorizontalDirection
import javafx.geometry.HorizontalDirection.LEFT
import javafx.geometry.HorizontalDirection.RIGHT
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import xenakis.impl.ScWriter
import xenakis.impl.SuperColliderContext
import xenakis.impl.getSerializableValue
import xenakis.impl.putSerializableValue
import xenakis.ui.ScoreObjectView

class ScoreObjectGroup(name: String, val score: Score) : AbstractScoreObject(name) {
    override val type: String
        get() = "compound"

    override val viewManager = ViewManager.createWeakViewManager<ScoreObjectView>()

    override fun addToScore(score: Score, context: Context) {
        super.addToScore(score, context)
        this.score.initialize(context)
    }

    override fun writeStartCode(writer: ScWriter, offset: Double) {
        score.writePlayerTask(writer, offset, taskName = "play_$name")
    }

    override fun writeStopCode(writer: ScWriter) {
        writer.appendLine("play_$name.stop;")
    }

    override fun cut(position: Double, whichHalf: HorizontalDirection): ScoreObject {
        val cutScore = Score()
        for (obj in score.objects) {
            when {
                whichHalf == LEFT && obj.start + obj.duration <= position -> {
                    cutScore.addObject(obj)
                }

                whichHalf == LEFT && obj.start < position -> {
                    obj.duration = position - obj.start
                    val leftHalf = obj.cut(position - obj.start, LEFT, obj.name + "_left")
                        ?: obj.also { it.duration = position - obj.start }
                    cutScore.addObject(leftHalf)
                }

                whichHalf == RIGHT && obj.start >= position -> {
                    obj.position.start -= position
                    cutScore.addObject(obj)
                }

                whichHalf == RIGHT && obj.start + obj.duration > position -> {
                    obj.position.start -= position
                    val rightHalf = obj.cut(position - obj.start, RIGHT, obj.name + "_right")
                        ?: obj.also { it.duration -= position - obj.start }
                    cutScore.addObject(rightHalf)
                }
            }
        }
        return ScoreObjectGroup(name, cutScore)
    }

    override fun serverBooted(context: SuperColliderContext) {
        super.serverBooted(context)
        for (obj in score.objects) {
            obj.serverBooted(context)
        }
    }

    override fun copy(): ScoreObject = ScoreObjectGroup(name, score.copy())

    override fun onRemove() {
        super.onRemove()
        for (obj in score.objects) {
            obj.onRemove()
        }
    }

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