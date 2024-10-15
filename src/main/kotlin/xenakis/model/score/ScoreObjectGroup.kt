package xenakis.model.score

import hextant.context.Context
import javafx.geometry.HorizontalDirection
import javafx.geometry.HorizontalDirection.LEFT
import javafx.geometry.HorizontalDirection.RIGHT
import javafx.geometry.VerticalDirection.DOWN
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import reaktive.value.ReactiveVariable
import reaktive.value.now
import reaktive.value.reactiveVariable
import xenakis.impl.Decimal
import xenakis.impl.copy
import xenakis.impl.zero
import xenakis.model.player.PlaybackManager
import xenakis.model.player.ScorePlayEnv
import xenakis.model.registry.ObjectReference
import xenakis.model.score.Score.Companion.rootScore
import xenakis.sc.client.SuperColliderContext
import xenakis.sc.editor.BusSelector
import xenakis.sc.editor.GroupSelector
import xenakis.ui.impl.Direction

@Serializable
class ScoreObjectGroup(
    @SerialName("name") override val mutableName: ReactiveVariable<String>,
    val score: Score,
    @SerialName("defaultGroup") val defaultGroupRef: ReactiveVariable<ObjectReference?> = reactiveVariable(null),
    @SerialName("defaultBus") val defaultBusRef: ReactiveVariable<ObjectReference?> = reactiveVariable(null)
) : ScoreObject() {
    override val type: String
        get() = "compound"

    override fun setContext(context: Context) {
        super.setContext(context)
        score.context = context
    }

    @Transient
    lateinit var groupSelector: GroupSelector
        private set

    @Transient
    lateinit var busSelector: BusSelector
        private set

    override fun initialize(context: Context) {
        if (initialized) return
        super.initialize(context)
        groupSelector = GroupSelector(context, defaultGroupRef)
        busSelector = BusSelector(context, preferredRate = null, preferredChannels = -1, defaultBusRef)
        this.score.initialize(context, name)
    }

    override fun writeCode(name: String, position: ObjectPosition, env: ScorePlayEnv): String = ""

    override fun doCut(position: Decimal, whichHalf: HorizontalDirection, newName: String): ScoreObject {
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
        val name = if (whichHalf == LEFT) "${name.now}_left" else "${name.now}_right"
        return ScoreObjectGroup(reactiveVariable(name), score, defaultGroupRef.copy(), defaultBusRef.copy())
    }

    fun cutVertically(position: Decimal): Pair<ScoreObjectGroup, ScoreObjectGroup> {
        val top = mutableListOf<ScoreObjectInstance>()
        val bottom = mutableListOf<ScoreObjectInstance>()
        for (inst in score.objectInstances) {
            if (inst.y < position) top.add(inst)
            else bottom.add(ScoreObjectInstance(inst.obj, inst.start, inst.y - position))
        }
        val name1 = reactiveVariable(name.now + "_top")
        val name2 = reactiveVariable(name.now + "_bot")
        val obj1 = ScoreObjectGroup(name1, Score(top), defaultBusRef.copy(), defaultGroupRef.copy())
        val obj2 = ScoreObjectGroup(name2, Score(bottom), defaultBusRef.copy(), defaultGroupRef.copy())
        obj1.setInitialSize(duration, position)
        obj2.setInitialSize(duration, height - position)
        return Pair(obj1, obj2)
    }

    override fun beginResize(type: ResizeType, direction: Direction): Boolean {
        super.beginResize(type, direction)
        if (type.isStretch || direction.left || direction.up) {
            for (inst in score.objectInstances) {
                inst.beginMove()
            }
            for (inst in context[rootScore].instancesOf(this)) {
                context[PlaybackManager].events.removedObject(inst.score!!, inst)
            }
        }
        if (type == ResizeType.DeepStretch) {
            for (obj in score.objects) {
                obj.beginResize(ResizeType.DeepStretch, Direction(RIGHT, DOWN))
            }
        }
        return true
    }

    override fun resize(targetDuration: Decimal, targetHeight: Decimal) {
        var minDur = zero(ObjectPosition.TIME_PRECISION)
        var minHeight = zero(ObjectPosition.Y_PRECISION)
        val objects = score.objectInstances
        if (objects.isNotEmpty() && !resizeType.isStretch) { //todo compute min dimensions when resizeType=Stretch
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
            if (resizeType.isStretch) {
                val factorT = this.duration / durationBeforeResize
                val factorY = this.height / heightBeforeResize
                val newTime = inst.positionBeforeMove.time * factorT
                val newY = inst.positionBeforeMove.y * factorY
                inst.moveTo(newTime, newY, simpleMove = false)
                if (resizeType == ResizeType.DeepStretch) {
                    val obj = inst.obj
                    obj.resize(obj.durationBeforeResize * factorT, obj.heightBeforeResize * factorY)
                }
            } else {
                val newTime = if (resizeDirection.left) inst.start + deltaDur else inst.start
                val newY = if (resizeDirection.up) inst.y + deltaHeight else inst.y
                inst.moveTo(newTime, newY, simpleMove = false)
            }
        }
    }

    override fun finishResize(recordEdit: Boolean) {
        super.finishResize(recordEdit)
        if (resizeType == ResizeType.DeepStretch) {
            for (obj in score.objects) {
                obj.finishResize(recordEdit = false)
            }
        }
        if (resizeType.isStretch || resizeDirection.left || resizeDirection.up) {
            for (inst in score.objectInstances) {
                inst.finishMove(notifyScore = false, recordEdit = false)
            }
        }
        for (inst in context[rootScore].instancesOf(this)) {
            context[PlaybackManager].events.addedObject(inst.score!!, inst)
        }
    }

    override fun serverBooted(context: SuperColliderContext) {
        super.serverBooted(context)
        for (obj in score.objects) {
            obj.serverBooted(context)
        }
    }

    override fun doClone(newName: String): ScoreObject =
        ScoreObjectGroup(reactiveVariable(newName), score.clone(), defaultGroupRef.copy(), defaultBusRef.copy())

    override fun onRemoved() {
        super.onRemoved()
        for (obj in score.objects) {
            obj.onRemoved()
        }
    }
}