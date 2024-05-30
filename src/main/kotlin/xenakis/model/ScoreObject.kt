package xenakis.model

import hextant.context.Context
import hextant.undo.UndoManager
import javafx.geometry.HorizontalDirection
import javafx.scene.input.DataFormat
import javafx.scene.paint.Color
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.serialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*
import kotlinx.serialization.serializer
import reaktive.value.ReactiveVariable
import reaktive.value.now
import reaktive.value.reactiveVariable
import xenakis.impl.*
import xenakis.sc.ControlSpec
import xenakis.sc.editor.AbstractRenamableObject
import xenakis.ui.ScoreObjectView

@Serializable(with = ScoreObject.Ser::class)
abstract class ScoreObject(name: String) : AbstractRenamableObject() {
    override val mutableName: ReactiveVariable<String> = reactiveVariable(name)

    private var initialized = false

    abstract val type: String

    lateinit var parent: Score
        private set
    abstract val position: ObjectPosition
    abstract var duration: Double
    abstract var height: Double
    abstract val start: Double
    abstract val y: Double

    abstract var associatedColor: Color?
    abstract var muted: Boolean

    abstract var nameOfNextInChain: String?
    abstract var nextInChain: ClonedObject?

    open val associatedControls: Map<String, ParameterControl> get() = emptyMap()
    abstract fun getSpec(parameter: String): ControlSpec

    abstract fun writeStartCode(writer: ScWriter, offset: Double, suffixGenerator: SuffixGenerator)
    abstract fun writeStopCode(writer: ScWriter, suffixGenerator: SuffixGenerator)

    abstract fun play(client: SuperColliderClient)

    protected fun recordEdit(edit: ScoreObjectEdit) {
        if (initialized) {
            context[UndoManager].record(edit)
        }
    }

    override fun canRenameTo(newName: String): Boolean = !context[NamingManager].isNameTaken(newName)

    override fun rename(newName: String) {
        if (name.now == newName) return
        recordEdit(ScoreObjectEdit.Rename(oldName = name.now, newName = newName, this))
        if (initialized) {
            parent.renamedObject(this, oldName = name.now, newName = newName)
            parent.context[NamingManager].renamedObject(this, oldName = name.now, newName = newName)
            parent.layoutManager.renamedObject(oldName = name.now, newName = newName)
        }
        super.rename(newName)
    }

    open fun addToScore(score: Score, context: Context) {
        initialized = true
        initialize(context)
        if (nameOfNextInChain != null) {
            nextInChain = score.getObject(nameOfNextInChain!!) as ClonedObject
            nameOfNextInChain = null
        }
        parent = score
    }

    open fun serverBooted(context: SuperColliderContext) {}

    open fun cut(position: Double, whichHalf: HorizontalDirection, newName: String): ScoreObject? = null

    abstract fun copy(newName: String): ScoreObject

    abstract fun clone(name: String): ClonedObject

    abstract fun addView(view: ScoreObjectView)

    abstract fun JsonObjectBuilder.saveToJson()

    open fun onRemove() {}

    interface Serializer {
        val type: String

        fun JsonObject.createFromJson(name: String): ScoreObject

        companion object {
            val all = listOf(
                MemoObject.Serializer,
                SynthObject.Serializer,
                SoundFileObject.Serializer,
                TaskObject.Serializer,
                EnvelopeObject.Serializer,
                ScoreObjectGroup.Serializer,
                ClonedObject.Serializer
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
            obj.position.start = json.getDouble("start") ?: 0.0
            obj.position.y = json.getDouble("y") ?: 0.0
            obj.nameOfNextInChain = json.getString("next")

            if (type != ClonedObject.Serializer.type) {
                obj.duration = json.getDouble("duration") ?: 0.0
                obj.height = json.getDouble("height") ?: 0.0
                obj.associatedColor = json.getColor("color")
                obj.muted = json.getBoolean("muted") ?: false
            }
            return obj
        }

        override fun serialize(encoder: Encoder, value: ScoreObject) {
            val type = value.type
            val obj = buildJsonObject {
                put("type", type)
                put("name", value.name.now)
                value.run { saveToJson() }
                if (value.start != 0.0) put("start", value.start)
                if (value.y != 0.0) put("y", value.y)
                if (value.nextInChain != null) put("next", value.nextInChain!!.name.now)
                if (type != ClonedObject.Serializer.type) {
                    if (value.duration != 0.0) put("duration", value.duration)
                    if (value.height != 0.0) put("height", value.height)
                    val color = value.associatedColor
                    if (color != null) put("color", JsonPrimitive(color.toString()))
                    if (value.muted) put("muted", JsonPrimitive(true))
                }
            }
            encoder.encodeSerializableValue(serializer<JsonObject>(), obj)
        }
    }

    companion object {
        val DATA_FORMAT = DataFormat("score-object")
    }
}