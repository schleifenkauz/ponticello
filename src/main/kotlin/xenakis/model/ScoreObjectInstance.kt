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

    val obj: ScoreObject get() = objectRef.get()

    fun addToScore(score: Score) {
        this.score = score
    }

    fun initialize(context: Context) {
        this.context = context
        objectRef.resolve(context[ScoreObjectRegistry])
    }

    fun addListener(listener: Listener) {
        viewManager.addListener(listener)
        //listener.toggledMute(muted)
    }

    fun removeListener(listener: Listener) {
        viewManager.removeListener(listener)
    }

    val start get() = _time
    val y get() = _y
    val position get() = ObjectPosition(start, y)

    val muted get() = _muted

    val duration get() = obj.duration
    val height get() = obj.height

    val end get() = start + duration
    val timeRange get() = start..end

    fun moveTo(time: Double, y: Double) {
        val before = ObjectPosition(this._time, this._y)
        val after = ObjectPosition(time, y)
        this._time = time
        this._y = y
        score.movedObject(this, before)
        context[UndoManager].record(MoveObject(this, before, after))
        viewManager.notifyListeners { moved(time, y) }
    }

    fun moveTo(position: ObjectPosition) {
        moveTo(position.time, position.y)
    }

    fun setTime(time: Double) {
        moveTo(time, _y)
    }

    fun setY(y: Double) {
        moveTo(_time, y)
    }

    fun toggleMuted() {
        _muted = !_muted
        context[UndoManager].record(ToggleMute(this))
        viewManager.notifyListeners { toggledMute(_muted) }
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
            clone.resize(dur, height, stretch = false, null, null)
            clone
        }
        return ScoreObjectInstance(half.createReference(), time, y, muted)
    }

    fun cut(position: Double): Pair<ScoreObjectInstance, ScoreObjectInstance> {
        val left = cut(position, HorizontalDirection.LEFT)
        val right = cut(position, HorizontalDirection.RIGHT)
        return left to right
    }

    override fun toString(): String = "instance of $obj, start: $start, y: $y"

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
            obj.moveTo(after.time, after.y)
        }

        override fun doUndo() {
            obj.moveTo(before.time, before.y)
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