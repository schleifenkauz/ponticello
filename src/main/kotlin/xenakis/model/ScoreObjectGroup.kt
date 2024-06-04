package xenakis.model

import hextant.context.Context
import hextant.core.editor.ListenerManager
import javafx.geometry.HorizontalDirection
import javafx.geometry.HorizontalDirection.LEFT
import javafx.geometry.HorizontalDirection.RIGHT
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import reaktive.value.now
import xenakis.impl.ScWriter
import xenakis.impl.SuperColliderContext
import xenakis.impl.getSerializableValue
import xenakis.impl.putSerializableValue
import xenakis.ui.ScoreObjectView

class ScoreObjectGroup(name: String, val score: Score) : RegularScoreObject(name) {
    override val type: String
        get() = "compound"

    override val viewManager = ListenerManager.createWeakListenerManager<ScoreObjectView>()

    override fun initialize(context: Context) {
        if (initialized) return
        super.initialize(context)
        this.score.initialize(context)
    }

    override fun writeStartCode(writer: ScWriter, offset: Double, name: String) {
        score.writePlayerTask(writer, offset, taskName = "play_$name", prefix = "_$name")
    }

    override fun writeStopCode(writer: ScWriter, name: String) {
        writer.appendLine("play_$name.stop;")
    }

    override fun cut(position: Double, whichHalf: HorizontalDirection): ScoreObject {
        val cutScore = Score()
        cutScore.initialize(context)
        for (obj in score.objects) {
            when {
                whichHalf == LEFT && obj.start + obj.duration <= position -> {
                    cutScore.addObject(obj)
                }

                whichHalf == LEFT && obj.start < position -> {
                    obj.duration = position - obj.start
                    val leftHalf = obj.cut(position - obj.start, LEFT, obj.name.now + "_left")
                        ?: obj.also { it.duration = position - obj.start }
                    cutScore.addObject(leftHalf)
                }

                whichHalf == RIGHT && obj.start >= position -> {
                    obj.position.start -= position
                    cutScore.addObject(obj)
                }

                whichHalf == RIGHT && obj.start + obj.duration > position -> {
                    obj.position.start -= position
                    val rightHalf = obj.cut(position - obj.start, RIGHT, obj.name.now + "_right")
                        ?: obj.also { it.duration -= position - obj.start }
                    cutScore.addObject(rightHalf)
                }
            }
        }
        return ScoreObjectGroup(name.now, cutScore)
    }

    override fun serverBooted(context: SuperColliderContext) {
        super.serverBooted(context)
        for (obj in score.objects) {
            obj.serverBooted(context)
        }
    }

    override fun copy(): ScoreObject = ScoreObjectGroup(name.now, score.copy())

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