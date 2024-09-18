package xenakis.model

import hextant.context.Context
import hextant.core.editor.ListenerManager
import javafx.geometry.HorizontalDirection
import javafx.geometry.HorizontalDirection.LEFT
import javafx.geometry.HorizontalDirection.RIGHT
import javafx.geometry.VerticalDirection.UP
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import reaktive.value.ReactiveVariable
import reaktive.value.now
import reaktive.value.reactiveVariable
import xenakis.impl.SuperColliderContext
import xenakis.ui.Direction
import xenakis.ui.ScoreObjectView

@Serializable
class ScoreObjectGroup(
    @SerialName("name") override val mutableName: ReactiveVariable<String>,
    val score: Score
) : ScoreObject() {
    override val type: String
        get() = "compound"

    override fun setContext(context: Context) {
        super.setContext(context)
        score.context = context
    }

    override fun initialize(context: Context) {
        if (initialized) return
        super.initialize(context)
        this.score.initialize(context, name)
    }

    override fun writeCode(
        name: String,
        position: ObjectPosition,
        env: ScorePlayEnv
    ): String {
        throw UnsupportedOperationException()
    }

    override fun doCut(position: Double, whichHalf: HorizontalDirection, newName: String): ScoreObject {
        val objects = mutableListOf<ScoreObjectInstance>()
        for (inst in score.objectInstances) {
            when {
                whichHalf == LEFT && inst.start + inst.duration <= position -> {
                    objects.add(inst)
                }

                whichHalf == LEFT && inst.start < position -> {
                    val leftHalf = inst.cut(position - inst.start, LEFT)
                    objects.add(leftHalf)
                }

                whichHalf == RIGHT && inst.start >= position -> {
                    inst.setTime(inst.start - position)
                    objects.add(inst)
                }

                whichHalf == RIGHT && inst.start + inst.duration > position -> {
                    val rightHalf = inst.cut(position - inst.start, RIGHT)
                    objects.add(rightHalf)
                }
            }
        }
        val score = Score(objects)
        val name = if (whichHalf == LEFT) "${name.now}_left" else "${name.now}_right"
        return ScoreObjectGroup(reactiveVariable(name), score)
    }

    override fun beginResize(stretch: Boolean, direction: Direction) {
        super.beginResize(stretch, direction)
        if (direction.left || direction.up) {
            for (inst in score.objectInstances) {
                inst.beginMove()
            }
        }
    }

    override fun resize(targetDuration: Double, targetHeight: Double) {
        var minDur = 0.0
        var minHeight = 0.0
        val objects = score.objectInstances
        if (objects.isNotEmpty()) {
            minDur =
                if (resizeDirection.left) this.duration - objects.minOf { o -> o.start }
                else objects.maxOf { o -> o.start + o.duration }

            minHeight =
                if (resizeDirection.up) this.height - objects.minOf { o -> o.y }
                else objects.maxOf { o -> o.y + o.height }
        }
        val deltaDur = targetDuration.coerceAtLeast(minDur) - this.duration
        val deltaHeight = targetHeight.coerceAtLeast(minHeight) - this.height
        super.resize(this.duration + deltaDur, this.height + deltaHeight)
        for (inst in score.objectInstances) {
            val newTime = if (resizeDirection.left) inst.start + deltaDur else inst.start
            val newY = if (resizeDirection.up) inst.y + deltaHeight else inst.y
            inst.moveTo(newTime, newY, simpleMove = false)
        }
    }

    override fun finishResize() {
        super.finishResize()
        if (resizeDirection.left || resizeDirection.up) {
            for (inst in score.objectInstances) {
                inst.finishMove(notifyScore = false)
            }
        }
    }

    override fun serverBooted(context: SuperColliderContext) {
        super.serverBooted(context)
        for (obj in score.objects) {
            obj.serverBooted(context)
        }
    }

    override fun doClone(newName: String): ScoreObject = ScoreObjectGroup(reactiveVariable(newName), score.clone())

    override fun onRemoved() {
        super.onRemoved()
        for (obj in score.objects) {
            obj.onRemoved()
        }
    }
}