package ponticello.model.score

import hextant.context.Context
import javafx.geometry.HorizontalDirection
import javafx.geometry.HorizontalDirection.LEFT
import javafx.geometry.HorizontalDirection.RIGHT
import javafx.geometry.Side
import kotlinx.serialization.Serializable
import ponticello.impl.Decimal
import ponticello.impl.zero
import ponticello.model.obj.withName
import ponticello.model.player.ObjectPlaybackInfo
import ponticello.sc.client.ScWriter
import reaktive.value.now

@Serializable
sealed class AbstractScoreObjectGroup : ScoreObject() {
    abstract val score: Score

    override val type: String
        get() = "compound"

    override val affectsPlayback: Boolean
        get() = score.objectInstances.any { inst -> inst.obj.affectsPlayback }


    override fun initialize(context: Context) {
        if (initialized) return
        super.initialize(context)
        this.score.initialize(context, this)
    }

    override fun activate() {
        if (!score.isAuxiliary) {
            for (inst in score.objectInstances) {
                inst.addedToScore(score)
            }
        }
    }

    override fun onRemoved() {
        if (!score.isAuxiliary) {
            for (inst in score.objectInstances) {
                inst.removedFromScore(Score.RegistryOption.ASK_IF_NEEDED)
            }
        }
    }

    protected abstract fun cloneWith(score: Score): AbstractScoreObjectGroup

    fun clone(newName: String, score: Score): AbstractScoreObjectGroup {
        val clone = cloneWith(score)
        copyBasicPropertiesTo(clone)
        clone.setInitialName(newName)
        return clone
    }

    override fun createInSuperCollider(writer: ScWriter) {
    }

    override fun ScWriter.startNewInstance(info: ObjectPlaybackInfo) {
        var t = zero
        score.allInstances().filter { inst ->
            inst.obj !is AbstractScoreObjectGroup && inst.obj.affectsPlayback
        }.sortedBy { inst -> inst.start }.forEach { inst ->
            val deltaT = (inst.start - info.cutoff).coerceAtLeast(zero) - t
            +"$deltaT.wait"
            +inst.obj.startNewInstance(
                info.copy(
                    instance = inst,
                    pos = info.pos + inst.position,
                    cutoff = (info.cutoff - inst.start).coerceAtLeast(zero),
                    extraArguments = emptyMap()
                )
            )
            t = inst.start
        }
    }

    override fun doCut(position: Decimal, whichHalf: HorizontalDirection): ScoreObject {
        val objects = mutableListOf<ScoreObjectInstance>()
        for (inst in score.objectInstances) {
            when {
                whichHalf == LEFT && inst.start + inst.duration <= position -> {
                    objects.add(inst)
                }

                whichHalf == LEFT && inst.start < position -> {
                    val leftHalf = inst.cut(position - inst.start, LEFT) ?: inst
                    objects.add(leftHalf)
                }

                whichHalf == RIGHT && inst.start >= position -> {
                    inst.setTime(inst.start - position)
                    objects.add(inst)
                }

                whichHalf == RIGHT && inst.start + inst.duration > position -> {
                    val rightHalf = inst.cut(position - inst.start, RIGHT) ?: continue
                    objects.add(rightHalf)
                }
            }
        }
        val score = Score(objects)
        return cloneWith(score)
    }

    fun cutVertically(position: Decimal): Pair<AbstractScoreObjectGroup, AbstractScoreObjectGroup> {
        val top = mutableListOf<ScoreObjectInstance>()
        val bottom = mutableListOf<ScoreObjectInstance>()
        for (inst in score.objectInstances) {
            if (inst.y < position) top.add(inst)
            else bottom.add(ScoreObjectInstance(inst.obj, inst.start, inst.y - position))
        }
        val obj1 = cloneWith(Score(top)).withName(name.now + "_top")
        val obj2 = cloneWith(Score(bottom)).withName(name.now + "_bot")
        obj1.setInitialSize(duration, position)
        obj2.setInitialSize(duration, height - position)
        return Pair(obj1, obj2)
    }

    override fun beginResize(mode: ResizeMode, side: Side): Boolean {
        super.beginResize(mode, side)
        if (mode.isStretch || side == Side.LEFT || side == Side.TOP) {
            for (inst in score.objectInstances) {
                inst.beginMove()
            }
        }
        if (mode == ResizeMode.DeepStretch) {
            for (obj in score.objects) {
                obj.beginResize(ResizeMode.DeepStretch, side)
            }
        }
        return true
    }

    protected open fun computeMinHeight(objects: List<ScoreObjectInstance>, resizeSide: Side) =
        if (resizeSide == Side.TOP) this.height - objects.minOf { o -> o.y }
        else objects.maxOf { o -> o.y + o.height }

    override fun resize(targetDuration: Decimal, targetHeight: Decimal) {
        if (targetDuration == this.duration && targetHeight == this.height) return
        var minDur = zero(ObjectPosition.TIME_PRECISION)
        var minHeight = zero(ObjectPosition.Y_PRECISION)
        val objects = score.objectInstances
        val mode = resizeMode ?: error("resizeMode is not set")
        if (objects.isNotEmpty() && !mode.isStretch) { //todo compute min dimensions when resizeType=Stretch
            minDur =
                if (resizeSide == Side.LEFT) this.duration - objects.minOf { o -> o.start }
                else objects.maxOf { o -> o.start + o.duration }

            minHeight = computeMinHeight(objects, resizeSide!!)
        }
        val deltaDur = targetDuration.coerceAtLeast(minDur) - this.duration
        val deltaHeight = targetHeight.coerceAtLeast(minHeight) - this.height
        super.resize(this.duration + deltaDur, this.height + deltaHeight)
        for (inst in score.objectInstances) {
            if (mode.isStretch) {
                val factorT = this.duration / durationBeforeResize
                val factorY = this.height / heightBeforeResize
                val newTime = inst.positionBeforeMove.time * factorT
                val newY = inst.positionBeforeMove.y * factorY
                inst.moveTo(newTime, newY, simpleMove = false)
                if (mode == ResizeMode.DeepStretch) {
                    val obj = inst.obj
                    obj.resize(obj.durationBeforeResize * factorT, obj.heightBeforeResize * factorY)
                }
            } else {
                val newTime = if (resizeSide == Side.LEFT) inst.start + deltaDur else inst.start
                val newY = if (resizeSide == Side.TOP) inst.y + deltaHeight else inst.y
                inst.moveTo(newTime, newY, simpleMove = false)
            }
        }
    }

    override fun finishResize(recordEdit: Boolean) {
        super.finishResize(recordEdit)
        val mode = resizeMode ?: error("resizeMode is not set")
        if (mode == ResizeMode.DeepStretch) {
            for (obj in score.objects) {
                obj.finishResize(recordEdit = false)
            }
        }
        if (mode.isStretch || resizeSide == Side.LEFT || resizeSide == Side.TOP) {
            for (inst in score.objectInstances) {
                inst.finishMove(notifyScore = false, recordEdit = false)
            }
        }
    }

    override fun doClone(): ScoreObject = cloneWith(score.clone())

    override fun deepClone(): ScoreObject = cloneWith(score.deepClone())
}