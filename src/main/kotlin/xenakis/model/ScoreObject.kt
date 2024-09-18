package xenakis.model

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
import xenakis.impl.SuperColliderContext
import xenakis.model.Score.Companion.rootScore
import xenakis.sc.ControlSpec
import xenakis.sc.NumericalControlSpec
import xenakis.ui.Direction

@Serializable
sealed class ScoreObject : AbstractRenamableObject() {
    abstract val type: String

    val associatedColor: ReactiveVariable<@Serializable(with = ColorSerializer::class) Color?> = reactiveVariable(null)

    open val associatedControls: Map<String, ParameterControl> get() = emptyMap()

    var duration: Double = 0.0
        protected set

    var height: Double = 0.0
        protected set

    @Transient
    private val viewManager: ListenerManager<Listener> = ListenerManager.createWeakListenerManager()

    @Transient
    protected var durationBeforeResize: Double = 0.0

    @Transient
    private var heightBeforeResize: Double = 0.0

    @Transient
    protected lateinit var resizeDirection: Direction

    @Transient
    protected var stretchResize: Boolean = false

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

    fun setInitialSize(duration: Double, height: Double) {
        this.duration = duration
        this.height = height
    }

    open fun beginResize(stretch: Boolean, direction: Direction) {
        durationBeforeResize = duration
        heightBeforeResize = height
        stretchResize = stretch
        resizeDirection = direction
        if (direction.left || direction.up) {
            for (inst in context[rootScore].instancesOf(this)) {
                inst.beginMove()
            }
        }
    }

    open fun resize(targetDuration: Double, targetHeight: Double) {
        if (!stretchResize) {
            for ((parameter, ctrl) in associatedControls) {
                if (ctrl !is EnvelopeControl) continue
                val spec = getSpec(parameter) as NumericalControlSpec
                if (resizeDirection.horizontal != null) {
                    ctrl.envelope.resize(targetDuration, resizeDirection.horizontal!!, spec)
                }
            }
        } else {
            for ((_, ctrl) in associatedControls) {
                if (ctrl !is EnvelopeControl) continue
                ctrl.envelope.rescale(targetDuration)
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

    open fun finishResize() {
        if (duration != durationBeforeResize || height != heightBeforeResize) {
            val deltaDuration = duration - durationBeforeResize
            val deltaHeight = height - heightBeforeResize
            viewManager.notifyListeners {
                finishedResize(this@ScoreObject, deltaDuration, deltaHeight, resizeDirection)
            }
            context[UndoManager].record(
                ResizeEdit(
                    this, durationBeforeResize, heightBeforeResize, this.duration, this.height,
                    stretchResize, resizeDirection
                )
            )
            if (resizeDirection.left || resizeDirection.up) {
                for (inst in context[rootScore].instancesOf(this)) {
                    inst.finishMove(notifyScore = false)
                }
            }
        }
    }

    fun resize(targetDuration: Double, targetHeight: Double, stretch: Boolean, direction: Direction) {
        beginResize(stretch, direction)
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

    protected open fun doCut(position: Double, whichHalf: HorizontalDirection, newName: String): ScoreObject? = null

    fun cut(position: Double, whichHalf: HorizontalDirection, newName: String): ScoreObject? {
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

    open fun getSpec(parameter: String): ControlSpec =
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

        fun finishedResize(obj: ScoreObject, deltaDuration: Double, deltaHeight: Double, direction: Direction) {}

        fun isSomeInstanceSelected(yesOrNo: Boolean) {}
    }

    private class ResizeEdit(
        private val obj: ScoreObject,
        private val oldDuration: Double,
        private val oldHeight: Double,
        private val newDuration: Double,
        private val newHeight: Double,
        private val stretch: Boolean,
        private val direction: Direction
    ) : AbstractEdit() {
        override val actionDescription: String
            get() = "Resize object"

        override fun doUndo() {
            obj.resize(oldDuration, oldHeight, stretch, direction)
        }

        override fun doRedo() {
            obj.resize(newDuration, newHeight, stretch, direction)
        }

        override fun mergeWith(other: Edit): Edit? {
            return when {
                other !is ResizeEdit -> null
                other.obj != this.obj -> null
                other.stretch != this.stretch -> null
                other.direction != this.direction -> null
                this.newDuration != other.oldDuration -> null
                this.newHeight != other.oldHeight -> null
                else -> ResizeEdit(
                    obj, oldDuration, oldHeight, other.newDuration, other.newHeight,
                    stretch, direction
                )
            }
        }
    }

    companion object {
        val DATA_FORMAT = DataFormat("score-object")
    }
}