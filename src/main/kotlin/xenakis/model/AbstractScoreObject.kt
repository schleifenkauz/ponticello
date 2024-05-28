package xenakis.model

import hextant.context.Context
import hextant.core.editor.ViewManager
import hextant.undo.UndoManager
import javafx.geometry.HorizontalDirection
import javafx.scene.paint.Color
import xenakis.impl.ScWriter
import xenakis.impl.UDPSuperColliderClient
import xenakis.sc.ControlSpec
import xenakis.ui.ScoreObjectView
import xenakis.ui.format

sealed class AbstractScoreObject(name: String) : ScoreObject {
    protected abstract val viewManager: ViewManager<out ScoreObjectView>

    private var initialized = false

    final override var name: String = name
        set(value) {
            if (field == value) return
            recordEdit(ScoreObjectEdit.Rename(oldName = field, newName = value, this))
            if (initialized) {
                parent.renamedObject(this, oldName = field, newName = value)
            }
            field = value
            viewManager.notifyViews { renamedObject() }
        }

    final override val position: ObjectPosition = ObjectPosition(this)
    final override var duration: Double = 0.0
        set(value) {
            if (value == field) return
            field = value
            viewManager.notifyViews { resized() }
        }

    final override var height: Double = 0.0
        set(value) {
            if (value == field) return
            field = value
            viewManager.notifyViews { resized() }
        }

    final override val start: Double by position::start
    final override val y: Double by position::y

    final override var associatedColor: Color? = null
        set(value) {
            if (field == value) return
            field = value
            viewManager.notifyViews { recoloredObject() }
        }

    final override var muted: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            recordEdit(ScoreObjectEdit.Mute(value, this))
            viewManager.notifyViews { muteToggled() }
        }

    final override var controls: List<ParameterControl> = emptyList()
        set(value) {
            if (field == value) return
            recordEdit(ScoreObjectEdit.ReassignControls(oldControls = field, newControls = value, this))
            field = value
            viewManager.notifyViews { reassignedControls() }
        }

    final override var nameOfNextInChain: String? = null
    final override var nextInChain: ClonedObject? = null

    override val associatedEnvelopes: List<EnvelopeControl> get() = controls.filterIsInstance<EnvelopeControl>()

    lateinit var context: Context
        private set

    final override lateinit var parent: Score
        private set

    private fun recordEdit(edit: ScoreObjectEdit) {
        if (initialized) {
            context[UndoManager].record(edit)
        }
    }

    override fun addToScore(score: Score, context: Context) {
        super.addToScore(score, context)
        this.context = context
        parent = score
        initialized = true
    }

    override fun writeStartCode(writer: ScWriter, offset: Double) {}

    override fun writeStopCode(writer: ScWriter) {}

    override fun clone(name: String): ClonedObject {
        val clone = ClonedObject(name, this)
        clone.position.set(this.position)
        return clone
    }

    protected abstract fun copy(): ScoreObject

    final override fun copy(newName: String): ScoreObject {
        val obj = copy()
        obj.name = newName
        obj.position.set(position)
        obj.duration = duration
        obj.height = height
        obj.associatedColor = associatedColor
        obj.muted = muted
        obj.controls = controls.mapTo(mutableListOf()) { c -> c.copy() }
        return obj
    }

    protected open fun cut(position: Double, whichHalf: HorizontalDirection): ScoreObject? = null

    final override fun cut(position: Double, whichHalf: HorizontalDirection, newName: String): ScoreObject? {
        val obj = cut(position, whichHalf) ?: return null
        obj.name = newName
        obj.height = height
        obj.associatedColor = associatedColor
        obj.muted = muted
        if (whichHalf == HorizontalDirection.LEFT) {
            obj.position.set(start, y)
            obj.duration = position
        } else {
            obj.position.set(start + position, y)
            obj.duration = duration - position
        }
        obj.controls = controls.mapTo(mutableListOf()) { c -> c.cut(position / duration, whichHalf) }
        return obj
    }

    override fun getSpec(parameter: String): ControlSpec =
        throw NoSuchElementException("no spec for parameter $parameter in $this")

    final override fun play(client: UDPSuperColliderClient) {
        client.postAsync {
            appendLine("Task{")
            writeStartCode(this, offset = 0.0)
            appendLine("${duration.format(2)}.wait;")
            writeStopCode(this)
            appendLine("}.play")
        }
    }

    final override fun addView(view: ScoreObjectView) {
        @Suppress("UNCHECKED_CAST")
        val unsafe = viewManager as ViewManager<ScoreObjectView>
        unsafe.addView(view)
    }
}