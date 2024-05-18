package xenakis.model

import hextant.context.Context
import hextant.core.editor.ViewManager
import hextant.undo.UndoManager
import javafx.scene.paint.Color
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.serialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*
import kotlinx.serialization.serializer
import xenakis.impl.*
import xenakis.sc.ControlSpec
import xenakis.ui.ScoreObjectView
import xenakis.ui.format

@Serializable(with = ScoreObject.Ser::class)
sealed class ScoreObject(name: String) {
    protected abstract val viewManager: ViewManager<out ScoreObjectView>

    abstract val type: String

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

    lateinit var context: Context

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

    protected abstract fun JsonObjectBuilder.saveToJson()

    interface Serializer {
        val type: String

        fun JsonObject.createFromJson(name: String): ScoreObject

        companion object {
            val all = listOf(
                MemoObject.Serializer,
                SynthObject.Serializer,
                SoundFileObject.Serializer,
                TaskObject.Serializer,
                EnvelopeObject.Serializer
            ).associateBy { it.type }
        }
    }

    object Ser : KSerializer<ScoreObject> {
        override val descriptor: SerialDescriptor = serialDescriptor<JsonObject>()

        override fun deserialize(decoder: Decoder): ScoreObject {
            val json = decoder.decodeSerializableValue(serializer<JsonObject>())
            val type = json.getValue("type").jsonPrimitive.content
            val ser = Serializer.all[type] ?: error("unknown score object type $type")
            val name = json.getString("name") ?: error("no name found for object of type $type")
            val obj = ser.run { json.createFromJson(name) }
            obj.start = json.getDouble("start") ?: 0.0
            obj.duration = json.getDouble("duration") ?: 0.0
            obj.y = json.getDouble("y") ?: 0.0
            obj.height = json.getDouble("height") ?: 0.0
            obj.associatedColor = json.getColor("color")
            obj.muted = json.getBoolean("muted") ?: false
            obj.controls = json.getSerializableValue("controls") ?: emptyList()
            return obj
        }

        override fun serialize(encoder: Encoder, value: ScoreObject) {
            val type = value.type
            val obj = buildJsonObject {
                put("type", type)
                put("name", value.name)
                value.run { saveToJson() }
                if (value.start != 0.0) {
                    put("start", value.start)
                }
                if (value.duration != 0.0) {
                    put("duration", value.duration)
                }
                if (value.y != 0.0) {
                    put("y", value.y)
                }
                if (value.height != 0.0) {
                    put("height", value.height)
                }
                val color = value.associatedColor
                if (color != null) put("color", JsonPrimitive(color.toString()))
                if (value.muted) put("muted", JsonPrimitive(true))
                if (value.controls.isNotEmpty()) putSerializableValue("controls", value.controls)
            }
            encoder.encodeSerializableValue(serializer<JsonObject>(), obj)
        }
    }
}