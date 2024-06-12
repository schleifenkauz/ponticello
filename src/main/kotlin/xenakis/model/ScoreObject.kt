package xenakis.model

import hextant.context.Context
import hextant.undo.Edit
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
import xenakis.impl.*
import xenakis.model.Score.Companion.rootScore
import xenakis.sc.ControlSpec
import xenakis.ui.ScoreObjectView

@Serializable(with = ScoreObject.Ser::class)
abstract class ScoreObject : AbstractRenamableObject() {
    abstract val type: String

    var parent: Score? = null
        private set

    abstract val position: ObjectPosition
    abstract var duration: Double
    abstract var height: Double
    abstract val start: Double
    abstract val y: Double

    abstract val associatedColor: ReactiveVariable<Color?>
    abstract var muted: Boolean

    abstract var nextInChain: Reference?

    open val associatedControls: Map<String, ParameterControl> get() = emptyMap()
    abstract fun getSpec(parameter: String): ControlSpec

    open fun writeStartCode(writer: ScWriter, offset: Double, name: String = this.name.now) {}

    open fun writeCode(writer: ScWriter, playAt: Double, name: String) {
        if (playAt < -duration) return
        val offset = -(playAt.coerceAtMost(0.0))
        writer.appendBlock("s.makeBundle(${(playAt).coerceAtLeast(0.0)})") {
            writeStartCode(writer, offset, name)
        }
        writer.appendLine(";")
    }

    abstract fun play(writer: ScWriter)

    protected fun recordEdit(edit: Edit) {
        if (initialized) {
            context[UndoManager].record(edit)
        }
    }

    override fun canRenameTo(newName: String): Boolean = !context[ScoreObjectRegistry].has(newName)

    override fun rename(newName: String) {
        if (name.now == newName) return
        if (initialized) recordEdit(ScoreObjectEdit.Rename(oldName = name.now, newName = newName, this))
        parent?.layoutManager?.renamedObject(oldName = name.now, newName = newName)
        super.rename(newName)
    }

    fun addToScore(score: Score) {
        parent = score
    }

    fun duplicateClone(): ScoreObject {
        val clone = clone()
        clone.position.start += duration
        parent!!.addObject(clone)
        return clone
    }

    fun duplicateCopy(): ScoreObject {
        val copied = if (this is ClonedObject) original else this
        val copy = copy(parent!!.nameForCopy(copied))
        copy.position.start += duration
        parent!!.addObject(copy)
        return copy
    }

    override fun initialize(context: Context) {
        if (initialized) return
        super.initialize(context)
        nextInChain?.resolve(context)
    }

    open fun serverBooted(context: SuperColliderContext) {}

    open fun cut(position: Double, whichHalf: HorizontalDirection, newName: String): ScoreObject? = null

    abstract fun copy(newName: String): ScoreObject

    abstract fun clone(): ClonedObject

    abstract fun addView(view: ScoreObjectView)

    abstract fun JsonObjectBuilder.saveToJson()

    override fun createReference(): Reference = Reference(this)

    @Serializable
    class Reference(private val subScoreName: String, private val name: String) : ObjectReference<ScoreObject> {
        private var obj: ScoreObject? = null

        constructor(obj: ScoreObject) : this(obj.parent!!.scoreName.now, obj.name.now) {
            this.obj = obj
        }

        override fun get(): ScoreObject = obj ?: error("ScoreObject not yet resolved")

        override fun resolve(context: Context) {
            if (obj != null) return
            val rootScore = context[rootScore]
            val score = if (subScoreName == "<root>") rootScore else rootScore.getSubScore(subScoreName)
            obj = score.getObject(name)
        }
    }

    interface Serializer {
        val type: String

        fun JsonObject.createFromJson(name: String): ScoreObject

        companion object {
            val all = listOf(
                MemoObject.Serializer,
                SynthObject.Serializer,
                PlayBufObject.Serializer,
                TaskObject.Serializer,
                EnvelopeObject.Serializer,
                ScoreObjectGroup.Serializer,
                PianoRollObject.Serializer,
                TempoGridObject.Serializer,
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
            obj.nextInChain = json.getSerializableValue("next")

            if (type != ClonedObject.Serializer.type) {
                obj.duration = json.getDouble("duration") ?: 0.0
                obj.height = json.getDouble("height") ?: 0.0
                obj.associatedColor.now = json.getColor("color")
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
                if (value.nextInChain != null) putSerializableValue("next", value.nextInChain!!)
                if (type != ClonedObject.Serializer.type) {
                    if (value.duration != 0.0) put("duration", value.duration)
                    if (value.height != 0.0) put("height", value.height)
                    val color = value.associatedColor.now
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