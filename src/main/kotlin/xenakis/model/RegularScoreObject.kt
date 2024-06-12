package xenakis.model

import hextant.core.editor.ListenerManager
import javafx.geometry.HorizontalDirection
import javafx.scene.paint.Color
import reaktive.value.ReactiveVariable
import reaktive.value.now
import reaktive.value.reactiveVariable
import xenakis.impl.ScWriter
import xenakis.sc.ControlSpec
import xenakis.ui.ScoreObjectView

sealed class RegularScoreObject(name: String) : ScoreObject() {
    protected abstract val viewManager: ListenerManager<out ScoreObjectView>

    override val mutableName: ReactiveVariable<String> = reactiveVariable(name)

    final override val position: ObjectPosition = ObjectPosition(this)
    final override var duration: Double = 0.0
        set(value) {
            if (value == field) return
            field = value
            viewManager.notifyListeners { resized() }
        }

    final override var height: Double = 0.0
        set(value) {
            if (value == field) return
            field = value
            viewManager.notifyListeners { resized() }
        }

    final override val start: Double by position::start
    final override val y: Double by position::y

    final override val associatedColor: ReactiveVariable<Color?> = reactiveVariable(null)

    final override var muted: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            recordEdit(ScoreObjectEdit.Mute(value, this))
            viewManager.notifyListeners { muteToggled() }
        }

    final override var nextInChain: Reference? = null

    override fun clone(): ClonedObject {
        val clone = ClonedObject(this)
        clone.position.set(this.position)
        return clone
    }

    protected abstract fun copy(): ScoreObject

    final override fun copy(newName: String): ScoreObject {
        val obj = copy()
        obj.rename(newName)
        obj.position.set(position)
        obj.duration = duration
        obj.height = height
        obj.associatedColor.now = associatedColor.now
        obj.muted = muted
        return obj
    }

    protected open fun cut(position: Double, whichHalf: HorizontalDirection): ScoreObject? = null

    final override fun cut(position: Double, whichHalf: HorizontalDirection, newName: String): ScoreObject? {
        val obj = cut(position, whichHalf) ?: return null
        obj.rename(newName)
        obj.height = height
        obj.associatedColor.now = associatedColor.now
        obj.muted = muted
        if (whichHalf == HorizontalDirection.LEFT) {
            obj.position.set(start, y)
            obj.duration = position
        } else {
            obj.position.set(start + position, y)
            obj.duration = duration - position
        }
        return obj
    }

    override fun getSpec(parameter: String): ControlSpec =
        throw NoSuchElementException("no spec for parameter $parameter in $this")

    final override fun play(writer: ScWriter) {
        writeStartCode(writer, offset = 0.0)
    }

    override fun addView(view: ScoreObjectView) {
        @Suppress("UNCHECKED_CAST")
        val unsafe = viewManager as ListenerManager<ScoreObjectView>
        unsafe.addListener(view)
    }
}