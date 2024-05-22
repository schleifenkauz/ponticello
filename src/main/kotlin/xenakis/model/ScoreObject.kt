package xenakis.model

import hextant.context.Context
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
import xenakis.impl.*
import xenakis.sc.ControlSpec
import xenakis.ui.ScoreObjectView

@Serializable(with = ScoreObject.Ser::class)
interface ScoreObject {
    val type: String
    var name: String

    val container: ScoreObjectContainer
    val position: ObjectPosition
    var duration: Double
    var height: Double
    val start: Double
    val y: Double

    var associatedColor: Color?
    var muted: Boolean

    var controls: List<ParameterControl>

    var nameOfNextInChain: String?
    var nextInChain: ClonedObject?

    val associatedEnvelopes: List<EnvelopeControl>
    fun getSpec(parameter: String): ControlSpec

    fun writeStartCode(writer: ScWriter, offset: Double)
    fun writeStopCode(writer: ScWriter)
    fun play(client: UDPSuperColliderClient)

    fun addToContainer(container: ScoreObjectContainer, context: Context) {
        if (nameOfNextInChain != null) {
            nextInChain = container.getObject(nameOfNextInChain!!) as ClonedObject
            nameOfNextInChain = null
        }
    }

    fun serverBooted(context: SuperColliderContext) {}

    fun copy(newName: String): ScoreObject

    fun clone(name: String): ClonedObject

    fun addView(view: ScoreObjectView)

    fun JsonObjectBuilder.saveToJson()

    fun onRemove() {}

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

            if (type != "clone") {
                obj.duration = json.getDouble("duration") ?: 0.0
                obj.height = json.getDouble("height") ?: 0.0
                obj.associatedColor = json.getColor("color")
                obj.muted = json.getBoolean("muted") ?: false
                obj.controls = json.getSerializableValue("controls") ?: emptyList()
            }
            return obj
        }

        override fun serialize(encoder: Encoder, value: ScoreObject) {
            val type = value.type
            val obj = buildJsonObject {
                put("type", type)
                put("name", value.name)
                value.run { saveToJson() }
                if (value.start != 0.0) put("start", value.start)
                if (value.y != 0.0) put("y", value.y)
                if (value.nextInChain != null) put("next", value.nextInChain!!.name)
                if (type != "clone") {
                    if (value.duration != 0.0) put("duration", value.duration)
                    if (value.height != 0.0) put("height", value.height)
                    val color = value.associatedColor
                    if (color != null) put("color", JsonPrimitive(color.toString()))
                    if (value.muted) put("muted", JsonPrimitive(true))
                    if (value.controls.isNotEmpty()) putSerializableValue("controls", value.controls)
                }
            }
            encoder.encodeSerializableValue(serializer<JsonObject>(), obj)
        }
    }

    companion object {
        val DATA_FORMAT = DataFormat("score-object")
    }
}