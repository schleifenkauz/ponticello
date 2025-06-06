package ponticello.model.score

import fxutils.undo.AbstractEdit
import fxutils.undo.Edit
import fxutils.undo.UndoManager
import hextant.context.Context
import hextant.core.editor.ListenerManager
import javafx.geometry.HorizontalDirection
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import ponticello.impl.Decimal
import ponticello.impl.copy
import ponticello.impl.rangeTo
import ponticello.model.obj.ScoreObjectReference
import ponticello.model.registry.ScoreObjectRegistry
import ponticello.model.registry.reference
import reaktive.value.ReactiveBoolean
import reaktive.value.ReactiveVariable
import reaktive.value.now
import reaktive.value.reactiveVariable

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
        if (obj !is ScoreObject.Unresolved) obj.addedToScore(context[ScoreObjectRegistry])
    }

    fun removedFromScore(option: Score.RegistryOption) {
        score = null
        if (obj !is ScoreObject.Unresolved) obj.removedFromScore(option)
    }

    fun initialize(context: Context) {
        this.context = context
        objectRef.resolve(context[ScoreObjectRegistry])
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
            score!!.removeObject(this, Score.RegistryOption.KEEP_IN_REGISTRY)
        } else {
            score!!.removeObject(this, Score.RegistryOption.KEEP_IN_REGISTRY)
            moveTo(position + relativePosition)
            newScore.addObject(this, autoSelect = false)
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
    ): ScoreObjectInstance = ScoreObjectInstance(obj.clone(newName), time, y, _muted.copy())

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

    fun replaceWith(obj: ScoreObject, autoSelect: Boolean) {
        val score = this.score ?: error("Cannot replace $this as it has no parent score")
        score.removeObject(this, Score.RegistryOption.ASK_IF_NEEDED)
        val newInst = ScoreObjectInstance(obj, this.position)
        score.addObject(newInst, autoSelect)
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