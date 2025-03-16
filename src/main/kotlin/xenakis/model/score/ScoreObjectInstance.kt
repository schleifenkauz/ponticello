package xenakis.model.score

import hextant.context.Context
import hextant.context.withoutUndo
import hextant.core.editor.ListenerManager
import hextant.undo.AbstractEdit
import hextant.undo.Edit
import hextant.undo.UndoManager
import javafx.geometry.HorizontalDirection
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import reaktive.value.ReactiveBoolean
import reaktive.value.ReactiveVariable
import reaktive.value.now
import reaktive.value.reactiveVariable
import xenakis.impl.*
import xenakis.model.obj.ScoreObjectReference
import xenakis.model.registry.ObjectReference
import xenakis.model.registry.ScoreObjectRegistry
import xenakis.model.registry.reference
import xenakis.model.score.Score.Companion.rootScore

@Serializable
class ScoreObjectInstance(
    @SerialName("object") private var objectRef: ScoreObjectReference,
    @SerialName("time") private var _time: Decimal,
    @SerialName("y") private var _y: Decimal,
    @SerialName("muted") private var _muted: ReactiveVariable<Boolean> = reactiveVariable(false)
) {
    constructor(
        obj: ScoreObject, time: Decimal, y: Decimal, muted: ReactiveVariable<Boolean> = reactiveVariable(false)
    ) : this(obj.reference(), time, y, muted)

    constructor(
        obj: ScoreObject, position: ObjectPosition, muted: ReactiveVariable<Boolean> = reactiveVariable(false)
    ) : this(obj, position.time, position.y, muted)

    @Transient
    private val viewManager = ListenerManager.createWeakListenerManager<Listener>()

    @Transient
    private lateinit var context: Context

    @Transient
    var score: Score? = null
        private set

    val start get() = _time
    val y get() = _y
    val position get() = ObjectPosition(start, y)

    val muted: ReactiveBoolean get() = _muted

    val duration get() = obj.duration
    val height get() = obj.height

    val end get() = start + duration
    val timeRange get() = start..end

    @Transient
    var positionBeforeMove = ObjectPosition.ZERO
        private set

    val ref get() = objectRef

    val obj: ScoreObject get() = objectRef.get() ?: ScoreObject.Unresolved()

    fun addedToScore(score: Score) {
        this.score = score
        val registry = context[ScoreObjectRegistry]
        val o = obj
        if (!registry.has(o.name.now)) {
            context.withoutUndo { registry.add(o) }
        }
        if (o is ScoreObjectGroup) {
            for (inst in o.score.objectInstances) {
                inst.addedToScore(o.score)
            }
        }
    }

    fun removedFromScore() {
        score = null
        val o = obj
        if (!context[rootScore].hasInstancesOf(o) && context[ScoreObjectRegistry].has(o)) {
            context.withoutUndo { context[ScoreObjectRegistry].remove(o) }
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
        //this is only for needed when opening projects that were created before the decimal-precision update
        _time = _time.withPrecision(ObjectPosition.TIME_PRECISION)
        _y = _y.withPrecision(ObjectPosition.Y_PRECISION)
    }

    fun addListener(listener: Listener) {
        viewManager.addListener(listener)
        listener.toggledMute(muted.now)
    }

    fun removeListener(listener: Listener) {
        viewManager.removeListener(listener)
    }

    fun beginMove() {
        positionBeforeMove = position
    }

    fun moveTo(time: Decimal, y: Decimal, simpleMove: Boolean) {
        if (this.start == time && this.y == y) return
        if (simpleMove) beginMove()
        this._time = time
        this._y = y
        viewManager.notifyListeners { moved(time, y) }
        if (simpleMove) finishMove()
    }

    fun finishMove(notifyScore: Boolean = true, recordEdit: Boolean = true) {
        if (position == positionBeforeMove) return
        if (notifyScore) score?.movedObject(this, positionBeforeMove)
        if (recordEdit) context[UndoManager].record(MoveObject(this, positionBeforeMove, position))
        positionBeforeMove = ObjectPosition.ZERO
    }

    fun moveTo(position: ObjectPosition) {
        moveTo(position.time, position.y, simpleMove = true)
    }

    fun setTime(time: Decimal) {
        moveTo(time, _y, simpleMove = true)
    }

    fun setY(y: Decimal) {
        moveTo(_time, y, simpleMove = true)
    }

    fun moveInto(newScore: Score, relativePosition: ObjectPosition, recurse: Boolean) {
        val obj = obj
        if (recurse && obj is ScoreObjectGroup) {
            for (inst in obj.score.objectInstances.toList()) {
                inst.moveInto(newScore, relativePosition + this.position, recurse = true)
            }
            score!!.removeObject(this)
        } else {
            score!!.removeObject(this)
            moveTo(position + relativePosition)
            newScore.addObject(this)
        }
    }

    fun toggleMuted() {
        if (obj.canMute) {
            _muted.now = !muted.now
            context[UndoManager].record(ToggleMute(this))
            viewManager.notifyListeners { toggledMute(muted.now) }
            score?.toggledMute(this, muted.now)
        }
    }

    fun duplicate(time: Decimal, y: Decimal) = ScoreObjectInstance(objectRef, time, y, _muted)

    fun duplicate(position: ObjectPosition) = duplicate(position.time, position.y)

    fun clone(
        time: Decimal = this.start, y: Decimal = this.y,
        newName: String = context[ScoreObjectRegistry].nameForClone(obj)
    ): ScoreObjectInstance = ScoreObjectInstance(obj.clone(newName), time, y, _muted)

    fun clone(
        position: ObjectPosition = this.position,
        newName: String = context[ScoreObjectRegistry].nameForClone(obj)
    ) = clone(position.time, position.y, newName)

    fun cut(position: Decimal, whichHalf: HorizontalDirection): ScoreObjectInstance? {
        val name = "${obj.name.now}_" + (if (whichHalf == HorizontalDirection.LEFT) "left" else "right")
        val time = if (whichHalf == HorizontalDirection.LEFT) start else start + position
        val half = obj.cut(position, whichHalf, name) ?: return null
        return ScoreObjectInstance(half, time, y, muted.copy())
    }

    fun cut(position: Decimal): Pair<ScoreObjectInstance, ScoreObjectInstance>? {
        val left = cut(position, HorizontalDirection.LEFT) ?: return null
        val right = cut(position, HorizontalDirection.RIGHT) ?: return null
        return left to right
    }

    fun replaceWith(obj: ScoreObject) {
        val score = this.score ?: error("Cannot replace $this as it has no parent score")
        score.removeObject(this)
        val newInst = ScoreObjectInstance(obj, this.position)
        score.addObject(newInst)
    }

    override fun toString(): String = "instance of $obj at $position in ${score?.scoreName?.now}"

    interface Listener {
        fun moved(start: Decimal, y: Decimal)

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