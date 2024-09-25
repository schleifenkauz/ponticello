package xenakis.model

import hextant.context.Context
import hextant.core.editor.ListenerManager
import hextant.undo.AbstractEdit
import hextant.undo.Edit
import hextant.undo.UndoManager
import javafx.geometry.HorizontalDirection
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import reaktive.value.now
import xenakis.model.Score.Companion.rootScore
import xenakis.ui.Direction

@Serializable
class ScoreObjectInstance(
    @SerialName("object") private var objectRef: ObjectReference,
    @SerialName("time") private var _time: Double,
    @SerialName("y") private var _y: Double,
    @SerialName("muted") private var _muted: Boolean = false
) {
    @Transient
    private val viewManager = ListenerManager.createWeakListenerManager<Listener>()

    @Transient
    private lateinit var context: Context

    @Transient
    lateinit var score: Score
        private set

    val start get() = _time
    val y get() = _y
    val position get() = ObjectPosition(start, y)

    val muted get() = _muted

    val duration get() = obj.duration
    val height get() = obj.height

    val end get() = start + duration
    val timeRange get() = start..end

    @Transient
    private var positionBeforeMove = position

    val obj: ScoreObject get() = objectRef.get()

    fun addedToScore(score: Score) {
        this.score = score
        val registry = context[ScoreObjectRegistry]
        val o = obj
        if (!registry.has(o.name.now)) registry.add(o)
        if (o is ScoreObjectGroup) {
            for (inst in o.score.objectInstances) {
                inst.addedToScore(o.score)
            }
        }
    }

    fun removedFromScore() {
        val o = obj
        if (!context[rootScore].hasInstancesOf(o)) {
            context[ScoreObjectRegistry].remove(o)
            if (o is ScoreObjectGroup) {
                for (subInst in o.score.objectInstances) {
                    subInst.removedFromScore()
                }
            }
        }
    }

    fun initialize(context: Context) {
        this.context = context
        objectRef.resolve(context[ScoreObjectRegistry])
    }

    fun addListener(listener: Listener) {
        viewManager.addListener(listener)
        listener.toggledMute(muted)
    }

    fun removeListener(listener: Listener) {
        viewManager.removeListener(listener)
    }

    fun beginMove() {
        positionBeforeMove = position
    }

    fun moveTo(time: Double, y: Double, simpleMove: Boolean) {
        if (this.start == time && this.y == y) return
        if (simpleMove) beginMove()
        this._time = time
        this._y = y
        viewManager.notifyListeners { moved(time, y) }
        if (simpleMove) finishMove()
    }

    fun finishMove(notifyScore: Boolean = true) {
        if (position == positionBeforeMove) return
        if (notifyScore) score.movedObject(this, positionBeforeMove)
        context[UndoManager].record(MoveObject(this, positionBeforeMove, position))
    }

    fun moveTo(position: ObjectPosition) {
        moveTo(position.time, position.y, simpleMove = true)
    }

    fun setTime(time: Double) {
        moveTo(time, _y, simpleMove = true)
    }

    fun setY(y: Double) {
        moveTo(_time, y, simpleMove = true)
    }

    fun toggleMuted() {
        if (obj.canMute) {
            _muted = !_muted
            context[UndoManager].record(ToggleMute(this))
            viewManager.notifyListeners { toggledMute(_muted) }
            score.toggledMute(this, muted)
        }
    }

    fun duplicate(time: Double, y: Double) = ScoreObjectInstance(objectRef, time, y, _muted)

    fun duplicate(position: ObjectPosition) = duplicate(position.time, position.y)

    fun clone(newName: String, time: Double, y: Double): ScoreObjectInstance {
        val clonedObj = obj.clone(newName)
        val ref = clonedObj.createReference()
        val inst = ScoreObjectInstance(ref, time, y, _muted)
        return inst
    }

    fun clone(newName: String, position: ObjectPosition) = clone(newName, position.time, position.y)

    fun cut(position: Double, whichHalf: HorizontalDirection): ScoreObjectInstance {
        val name = "${obj.name.now}_" + (if (whichHalf == HorizontalDirection.LEFT) "left" else "right")
        val time = if (whichHalf == HorizontalDirection.LEFT) start else start + position
        val half = obj.cut(position, whichHalf, name) ?: run {
            val clone = obj.clone(name)
            val dur = if (whichHalf == HorizontalDirection.LEFT) position else obj.duration - position
            clone.resize(dur, height, stretch = false, direction = Direction.NONE)
            clone
        }
        return ScoreObjectInstance(half.createReference(), time, y, muted)
    }

    fun cut(position: Double): Pair<ScoreObjectInstance, ScoreObjectInstance> {
        val left = cut(position, HorizontalDirection.LEFT)
        val right = cut(position, HorizontalDirection.RIGHT)
        return left to right
    }

    override fun toString(): String = "instance of $obj at $position in ${score.scoreName.now}"

    interface Listener {
        fun moved(start: Double, y: Double)

        fun toggledMute(muted: Boolean)
    }

    class MoveObject(
        private val obj: ScoreObjectInstance,
        private val before: ObjectPosition,
        private val after: ObjectPosition,
    ) : AbstractEdit() {
        override val actionDescription: String
            get() = "Move object"

        override fun doRedo() {
            obj.moveTo(after.time, after.y, simpleMove = true)
        }

        override fun doUndo() {
            obj.moveTo(before.time, before.y, simpleMove = true)
        }

        override fun mergeWith(other: Edit): Edit? {
            if (other is MoveObject && other.obj == this.obj) {
                return MoveObject(obj, this.before, other.after)
            }
            return null
        }
    }

    private class ToggleMute(private val obj: ScoreObjectInstance) : AbstractEdit() {
        override val actionDescription: String
            get() = "Toggle Muted"

        override fun doUndo() {
            obj.toggleMuted()
        }

        override fun doRedo() {
            obj.toggleMuted()
        }
    }
}