package xenakis.model

import hextant.context.Context
import hextant.core.editor.ViewManager
import hextant.undo.UndoManager
import javafx.scene.paint.Color
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.serialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonObject
import xenakis.impl.ScWriter
import xenakis.impl.UDPSuperColliderClient
import xenakis.sc.ControlSpec
import xenakis.ui.ScoreObjectView
import xenakis.ui.format

@Serializable(with = ScoreObject.Serializer::class)
sealed class ScoreObject(name: String) {
    protected abstract val viewManager: ViewManager<out ScoreObjectView>

    @Transient
    private var initialized = false

    var name: String = name
        set(value) {
            if (field == value) return
            recordEdit(ScoreObjectEdit.Rename(oldName = field, newName = value, this))
            container.renamedObject(this, oldName = field, newName = value)
            field = value
            viewManager.notifyViews { renamedObject() }
        }

    var start: Double = 0.0
    var duration: Double = 0.0
    var y: Double = 0.0
    var height: Double = 0.0

    var associatedColor: Color? = null
        set(value) {
            if (field == value) return
            field = value
            viewManager.notifyViews { recoloredObject() }
        }

    var muted: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            recordEdit(ScoreObjectEdit.Mute(value, this))
            viewManager.notifyViews { muteToggled() }
        }

    var controls: List<ParameterControl> = emptyList()
        set(value) {
            if (field == value) return
            recordEdit(ScoreObjectEdit.ReassignControls(oldControls = field, newControls = value, this))
            field = value
            viewManager.notifyViews { reassignedControls() }
        }

    open val associatedEnvelopes: List<EnvelopeControl> get() = controls.filterIsInstance<EnvelopeControl>()

    @Transient
    lateinit var context: Context

    @Transient
    lateinit var container: ScoreObjectContainer
        private set

    private fun recordEdit(edit: ScoreObjectEdit) {
        if (initialized) {
            context[UndoManager].record(edit)
        }
    }

    fun moveTo(time: Double, y: Double) {
        start = time
        this.y = y
    }

    open fun addToContainer(container: ScoreObjectContainer, context: Context) {
        this.context = context
        this.container = container
        initialized = true
    }

    open fun onRemove() {}

    open fun writeStartCode(writer: ScWriter, offset: Double) {}

    open fun writeStopCode(writer: ScWriter) {}

    protected abstract fun clone(): ScoreObject

    fun clone(newName: String): ScoreObject {
        val obj = clone()
        obj.name = newName
        obj.start = this.start
        obj.duration = this.duration
        obj.y = this.y
        obj.height = this.height
        obj.associatedColor = this.associatedColor
        obj.muted = this.muted
        obj.controls = controls.mapTo(mutableListOf()) { c -> c.clone() }
        return obj
    }

    open fun getSpec(parameter: String): ControlSpec =
        throw NoSuchElementException("no spec for parameter $parameter in $this")

    fun play(client: UDPSuperColliderClient) {
        client.postAsync {
            appendLine("Task{")
            writeStartCode(this, offset = 0.0)
            appendLine("${duration.format(2)}.wait;")
            writeStopCode(this)
            appendLine("}.play")
        }
    }

    fun addView(view: ScoreObjectView) {
        @Suppress("UNCHECKED_CAST")
        val unsafe = viewManager as ViewManager<ScoreObjectView>
        unsafe.addView(view)
    }

    object Serializer : KSerializer<ScoreObject> {
        override val descriptor: SerialDescriptor = serialDescriptor<JsonObject>()

        override fun deserialize(decoder: Decoder): ScoreObject {
            TODO("Not yet implemented")
        }

        override fun serialize(encoder: Encoder, value: ScoreObject) {
            TODO("Not yet implemented")
        }
    }
}