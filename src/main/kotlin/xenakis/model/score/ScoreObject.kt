package xenakis.model.score

import hextant.core.editor.ListenerManager
import hextant.undo.AbstractEdit
import hextant.undo.Edit
import hextant.undo.UndoManager
import javafx.geometry.HorizontalDirection
import javafx.geometry.HorizontalDirection.LEFT
import javafx.scene.input.DataFormat
import javafx.scene.paint.Color
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import reaktive.value.ReactiveVariable
import reaktive.value.now
import reaktive.value.reactiveVariable
import xenakis.impl.ColorSerializer
import xenakis.impl.Decimal
import xenakis.impl.withPrecision
import xenakis.impl.zero
import xenakis.model.obj.AbstractRenamableObject
import xenakis.model.player.ScorePlayEnv
import xenakis.model.registry.ScoreObjectRegistry
import xenakis.model.score.Score.Companion.rootScore
import xenakis.sc.ControlSpec
import xenakis.sc.NumericalControlSpec
import xenakis.sc.client.SuperColliderContext
import xenakis.ui.impl.Direction

@Serializable
sealed class ScoreObject : AbstractRenamableObject() {
    abstract val type: String
    open val canMute: Boolean get() = true
    val associatedColor: ReactiveVariable<@Serializable(with = ColorSerializer::class) Color?> = reactiveVariable(null)

    open val associatedControls: Map<String, ParameterControl> get() = emptyMap()

    open val canResize: Boolean get() = true

    var duration: Decimal = zero(ObjectPosition.TIME_PRECISION)
        protected set

    var height: Decimal = zero(ObjectPosition.Y_PRECISION)
        protected set

    @Transient
    private val viewManager: ListenerManager<Listener> = ListenerManager.createWeakListenerManager()

    @Transient
    var durationBeforeResize: Decimal = zero(ObjectPosition.TIME_PRECISION)
        private set

    @Transient
    var heightBeforeResize: Decimal = zero(ObjectPosition.Y_PRECISION)
        private set

    @Transient
    protected lateinit var resizeDirection: Direction

    @Transient
    protected var resizeType: ResizeType = ResizeType.Regular

    init {
        //this is only for needed when opening projects that were created before the decimal-precision update
        duration = duration.withPrecision(ObjectPosition.TIME_PRECISION)
        height = height.withPrecision(ObjectPosition.Y_PRECISION)
    }

    abstract fun writeCode(name: String, position: ObjectPosition, env: ScorePlayEnv): String

    protected fun recordEdit(edit: Edit) {
        if (initialized) {
            context[UndoManager].record(edit)
        }
    }

    override fun canRenameTo(newName: String): Boolean = !context[ScoreObjectRegistry].has(newName)

    override fun rename(newName: String) {
        if (name.now == newName) return
        if (initialized) recordEdit(ScoreObjectEdit.Rename(oldName = name.now, newName = newName, this))
        super.rename(newName)
    }

    open fun serverBooted(context: SuperColliderContext) {}

    fun setInitialSize(duration: Decimal, height: Decimal) {
        this.duration = duration
        this.height = height
    }

    open fun beginResize(type: ResizeType, direction: Direction): Boolean {
        durationBeforeResize = duration
        heightBeforeResize = height
        resizeType = type
        resizeDirection = direction
        return type != ResizeType.DeepStretch
    }

    open fun resize(targetDuration: Decimal, targetHeight: Decimal) {
        when (resizeType) {
            ResizeType.Regular -> {
                for ((parameter, ctrl) in associatedControls) {
                    if (ctrl !is EnvelopeControl) continue
                    val spec = getSpec(parameter) as? NumericalControlSpec
                    if (spec != null && resizeDirection.horizontal != null) {
                        ctrl.envelope.resize(targetDuration, resizeDirection.horizontal!!, spec)
                    }
                }
            }

            ResizeType.Stretch, ResizeType.DeepStretch -> {
                for ((_, ctrl) in associatedControls) {
                    if (ctrl !is EnvelopeControl) continue
                    ctrl.envelope.rescale(targetDuration)
                }
            }
        }
        val deltaDur = targetDuration - duration
        val deltaHeight = targetHeight - height
        this.duration = targetDuration
        this.height = targetHeight
        for (inst in context[rootScore].instancesOf(this)) {
            val instTime = if (resizeDirection.left) inst.start - deltaDur else inst.start
            val instY = if (resizeDirection.up) inst.y - deltaHeight else inst.y
            inst.moveTo(instTime, instY, simpleMove = false)
        }
        viewManager.notifyListeners { resizedObject(this@ScoreObject) }
    }

    open fun finishResize(recordEdit: Boolean = true) {
        if (duration == durationBeforeResize && height == heightBeforeResize) return
        val deltaDuration = duration - durationBeforeResize
        val deltaHeight = height - heightBeforeResize
        viewManager.notifyListeners {
            finishedResize(this@ScoreObject, deltaDuration, deltaHeight, resizeDirection)
        }
        if (recordEdit) {
            context[UndoManager].record(
                ResizeEdit(
                    this, durationBeforeResize, heightBeforeResize, this.duration, this.height,
                    resizeType, resizeDirection
                )
            )
        }
    }

    fun resize(targetDuration: Decimal, targetHeight: Decimal, type: ResizeType, direction: Direction) {
        beginResize(type, direction)
        resize(targetDuration, targetHeight)
        finishResize()
    }

    protected abstract fun doClone(newName: String): ScoreObject

    fun clone(newName: String): ScoreObject {
        val obj = doClone(newName)
        obj.duration = duration
        obj.height = height
        obj.associatedColor.now = associatedColor.now
        obj.initialize(context)
        return obj
    }

    protected open fun doCut(position: Decimal, whichHalf: HorizontalDirection, newName: String): ScoreObject? {
        val clone = clone(newName)
        val dur = if (whichHalf == LEFT) position else duration - position
        clone.resize(dur, height, ResizeType.Regular, direction = Direction.NONE)
        return clone
    }

    fun cut(position: Decimal, whichHalf: HorizontalDirection, newName: String): ScoreObject? {
        val obj = doCut(position, whichHalf, newName) ?: return null
        obj.rename(newName)
        obj.height = height
        obj.associatedColor.now = associatedColor.now
        if (whichHalf == LEFT) {
            obj.duration = position
        } else {
            obj.duration = duration - position
        }
        obj.initialize(context)
        return obj
    }

    open fun getSpec(parameter: String): ControlSpec? =
        throw NoSuchElementException("no spec for parameter $parameter in $this")

    fun notifyListeners(action: Listener.() -> Unit) {
        viewManager.notifyListeners(action)
    }

    @JvmName("notifyListenersGeneric")
    inline fun <reified L : Listener> notifyListeners(crossinline action: L.() -> Unit) {
        notifyListeners { if (this is L) action() }
    }

    open fun addListener(view: Listener) {
        viewManager.addListener(view)
    }

    fun removeListener(listener: Listener) {
        viewManager.removeListener(listener)
    }

    interface Listener {
        fun resizedObject(obj: ScoreObject) {}

        fun finishedResize(obj: ScoreObject, deltaDuration: Decimal, deltaHeight: Decimal, direction: Direction) {}

        fun isSomeInstanceSelected(yesOrNo: Boolean) {}
    }

    private class ResizeEdit(
        private val obj: ScoreObject,
        private val oldDuration: Decimal,
        private val oldHeight: Decimal,
        private val newDuration: Decimal,
        private val newHeight: Decimal,
        private val type: ResizeType,
        private val direction: Direction
    ) : AbstractEdit() {
        override val actionDescription: String
            get() = "Resize object"

        override fun doUndo() {
            obj.resize(oldDuration, oldHeight, type, direction)
        }

        override fun doRedo() {
            obj.resize(newDuration, newHeight, type, direction)
        }

        override fun mergeWith(other: Edit): Edit? {
            return when {
                other !is ResizeEdit -> null
                other.obj != this.obj -> null
                other.type != this.type -> null
                other.direction != this.direction -> null
                this.newDuration != other.oldDuration -> null
                this.newHeight != other.oldHeight -> null
                else -> ResizeEdit(
                    obj, oldDuration, oldHeight, other.newDuration, other.newHeight,
                    type, direction
                )
            }
        }
    }

    enum class ResizeType {
        Regular, Stretch, DeepStretch;

        val isStretch: Boolean get() = this == Stretch || this == DeepStretch
    }

    companion object {
        val DATA_FORMAT = DataFormat("score-object")
    }

    @Serializable
    class Unresolved(override val mutableName: ReactiveVariable<String>) : ScoreObject() {

        override val type: String
            get() = "none"

        override fun writeCode(name: String, position: ObjectPosition, env: ScorePlayEnv): String = ""

        override fun doClone(newName: String): ScoreObject = this
    }
}