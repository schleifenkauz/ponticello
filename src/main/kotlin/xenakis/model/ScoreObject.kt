package xenakis.model

import hextant.core.editor.ListenerManager
import hextant.undo.Edit
import hextant.undo.UndoManager
import javafx.geometry.HorizontalDirection
import javafx.scene.input.DataFormat
import javafx.scene.paint.Color
import kotlinx.serialization.Serializable
import reaktive.value.ReactiveVariable
import reaktive.value.now
import reaktive.value.reactiveVariable
import xenakis.impl.ColorSerializer
import xenakis.impl.SuperColliderContext
import xenakis.sc.ControlSpec
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

    override fun canRenameTo(newName: String): Boolean = context[ScoreObjectRegistry].has(newName)

    override fun rename(newName: String) {
        if (name.now == newName) return
        if (initialized) recordEdit(ScoreObjectEdit.Rename(oldName = name.now, newName = newName, this))
        super.rename(newName)
    }

    open fun serverBooted(context: SuperColliderContext) {}

    protected abstract val viewManager: ListenerManager<out ScoreObjectView>

    var duration: Double = 0.0
        set(value) {
            if (value == field) return
            field = value
            viewManager.notifyListeners { resized() }
        }

    var height: Double = 0.0
        set(value) {
            if (value == field) return
            field = value
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

    companion object {
        val DATA_FORMAT = DataFormat("score-object")
    }
}