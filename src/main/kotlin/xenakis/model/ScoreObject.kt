package xenakis.model

import hextant.core.editor.ListenerManager
import hextant.undo.AbstractEdit
import hextant.undo.Edit
import hextant.undo.UndoManager
import javafx.geometry.HorizontalDirection
import javafx.geometry.VerticalDirection
import javafx.scene.input.DataFormat
import javafx.scene.paint.Color
import kotlinx.serialization.Serializable
import reaktive.value.ReactiveVariable
import reaktive.value.now
import reaktive.value.reactiveVariable
import xenakis.impl.ColorSerializer
import xenakis.impl.SuperColliderContext
import xenakis.model.Score.Companion.rootScore
import xenakis.sc.ControlSpec
import xenakis.sc.NumericalControlSpec
import xenakis.ui.ScoreObjectView

@Serializable
sealed class ScoreObject : AbstractRenamableObject() {
    abstract val type: String

    val associatedColor: ReactiveVariable<@Serializable(with = ColorSerializer::class) Color?> = reactiveVariable(null)

    open val associatedControls: Map<String, ParameterControl> get() = emptyMap()

    abstract fun writeCode(env: ScorePlayEnv, name: String, cutoff: Double): String

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

    protected abstract val viewManager: ListenerManager<out ScoreObjectView>

    var duration: Double = 0.0
        protected set

    var height: Double = 0.0
        protected set

    fun setInitialSize(duration: Double, height: Double) {
        this.duration = duration
        this.height = height
    }

    open fun resize(
        duration: Double, height: Double, stretch: Boolean,
        horizontalDirection: HorizontalDirection?, verticalDirection: VerticalDirection?
    ) {
        if (!stretch) {
            for ((parameter, ctrl) in associatedControls) {
                if (ctrl !is EnvelopeControl) continue
                val spec = getSpec(parameter) as NumericalControlSpec
                if (horizontalDirection != null) ctrl.envelope.resize(duration, horizontalDirection, spec)
            }
        } else {
            for ((_, ctrl) in associatedControls) {
                if (ctrl !is EnvelopeControl) continue
                ctrl.envelope.rescale(duration)
            }
        }
        val oldDuration = this.duration
        val oldHeight = this.height
        val deltaDur = duration - oldDuration
        val deltaHeight = height - oldHeight
        this.duration = duration
        this.height = height
        for (inst in context[rootScore].instancesOf(this)) {
            if (horizontalDirection == HorizontalDirection.LEFT) inst.setTime(inst.time - deltaDur)
            if (verticalDirection == VerticalDirection.UP) inst.setY(inst.y - deltaHeight)
        }
        context[UndoManager].record(
            ResizeEdit(
                this, oldDuration, oldHeight, duration, height,
                stretch, horizontalDirection, verticalDirection
            )
        )
        viewManager.notifyListeners { resized() }
    }

    protected abstract fun doClone(newName: String): ScoreObject

    fun clone(newName: String): ScoreObject {
        val obj = doClone(newName)
        obj.duration = duration
        obj.height = height
        obj.associatedColor.now = associatedColor.now
        return obj
    }

    protected open fun doCut(position: Double, whichHalf: HorizontalDirection, newName: String): ScoreObject? = null

    fun cut(position: Double, whichHalf: HorizontalDirection, newName: String): ScoreObject? {
        val obj = doCut(position, whichHalf, newName) ?: return null
        obj.rename(newName)
        obj.height = height
        obj.associatedColor.now = associatedColor.now
        if (whichHalf == HorizontalDirection.LEFT) {
            obj.duration = position
        } else {
            obj.duration = duration - position
        }
        return obj
    }

    open fun getSpec(parameter: String): ControlSpec =
        throw NoSuchElementException("no spec for parameter $parameter in $this")

    open fun addView(view: ScoreObjectView) {
        @Suppress("UNCHECKED_CAST")
        val unsafe = viewManager as ListenerManager<ScoreObjectView>
        unsafe.addListener(view)
    }

    private class ResizeEdit(
        private val obj: ScoreObject,
        private val oldDuration: Double,
        private val oldHeight: Double,
        private val newDuration: Double,
        private val newHeight: Double,
        private val stretch: Boolean,
        private val horizontalDirection: HorizontalDirection?,
        private val verticalDirection: VerticalDirection?
    ) : AbstractEdit() {
        override val actionDescription: String
            get() = "Resize object"

        override fun doUndo() {
            obj.resize(oldDuration, oldHeight, stretch, horizontalDirection, verticalDirection)
        }

        override fun doRedo() {
            obj.resize(newDuration, newHeight, stretch, horizontalDirection, verticalDirection)
        }

        override fun mergeWith(other: Edit): Edit? {
            return when {
                other !is ResizeEdit -> null
                other.obj != this.obj -> null
                other.stretch != this.stretch -> null
                other.horizontalDirection != this.horizontalDirection -> null
                other.verticalDirection != this.verticalDirection -> null
                this.newDuration != other.oldDuration -> null
                this.newHeight != other.oldHeight -> null
                else -> ResizeEdit(
                    obj, oldDuration, oldHeight, other.newDuration, other.newHeight,
                    stretch, horizontalDirection, verticalDirection
                )
            }
        }
    }

    companion object {
        val DATA_FORMAT = DataFormat("score-object")
    }
}