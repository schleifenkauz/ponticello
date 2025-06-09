package ponticello.model.registry

import bundles.PublicProperty
import bundles.publicProperty
import bundles.set
import hextant.context.Context
import hextant.serial.string
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.serialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*
import ponticello.impl.parseDecimal
import ponticello.model.obj.*
import ponticello.sc.Identifier
import reaktive.Observer
import reaktive.value.ReactiveVariable
import reaktive.value.now
import reaktive.value.reactiveVariable
import java.io.File

@Serializable(with = BufferRegistry.Serializer::class)
class BufferRegistry(
    override val objects: MutableList<BufferObject> = mutableListOf(),
    val copyAudioFiles: ReactiveVariable<Boolean>,
) : SuperColliderObjectRegistry<BufferObject>() {
    override val objectType: String
        get() = "Buffer"
    override val liveCycleType: SuperColliderObject.LiveCycleType
        get() = SuperColliderObject.LiveCycleType.ServerBoot

    @Transient
    private val copyObserver: Observer = copyAudioFiles.observe { _, _, copy ->
        for (obj in objects) {
            if (obj is SampleObject) {
                obj.toggleCopyToSamplesDir(copy)
            }
        }
    }

    override fun initialize(context: Context) {
        context[BufferRegistry] = this
        super.initialize(context)
    }

    fun getSample(file: File): SampleObject? = filterIsInstance<SampleObject>().find { o -> o.audioFile == file }

    fun getOrAdd(file: File): SampleObject = getSample(file) ?: run {
        val name = Identifier.truncate(file.nameWithoutExtension)
        val sample = SampleObject.create(context.project, name, file)
        add(sample)
        return sample
    }

    object Serializer : KSerializer<BufferRegistry> {
        override val descriptor: SerialDescriptor
            get() = serialDescriptor<JsonObject>()

        override fun serialize(encoder: Encoder, value: BufferRegistry) {
            val obj = buildJsonObject {
                put("copyAudioFiles", JsonPrimitive(value.copyAudioFiles.now))
                for (buf in value.objects) {
                    val content = when (buf) {
                        is SampleObject -> JsonPrimitive(buf.filePath().now)
                        is AllocatedBufferObject -> buildJsonObject {
                            put("channels", JsonPrimitive(buf.channels.now))
                            put("duration", JsonPrimitive(buf.duration.now))
                        }
                    }
                    put(buf.name.now, content)
                }
            }
            encoder.encodeSerializableValue(JsonObject.serializer(), obj)

        }

        override fun deserialize(decoder: Decoder): BufferRegistry {
            val obj = decoder.decodeSerializableValue(JsonObject.serializer())
            val copyAudioFiles = obj.getValue("copyAudioFiles").jsonPrimitive.boolean
            val objects = mutableListOf<BufferObject>()
            for ((name, content) in obj) {
                if (name == "copyAudioFiles") continue
                val buf = when (content) {
                    is JsonPrimitive -> SampleObject(reactiveVariable(content.string)).withName(name)
                    is JsonObject -> {
                        val channels = content["channels"]?.jsonPrimitive?.int
                            ?: error("Missing 'channels' in object: $content")
                        val duration = content["duration"]?.jsonPrimitive?.content?.parseDecimal()
                            ?: error("Missing 'duration' in object: $content")
                        AllocatedBufferObject(
                            reactiveVariable(channels),
                            reactiveVariable(duration)
                        ).withName(name)
                    }

                    else -> error("Unknown buffer content type: $content")
                }
                objects.add(buf)
            }
            return BufferRegistry(objects, reactiveVariable(copyAudioFiles))
        }
    }

    companion object : PublicProperty<BufferRegistry> by publicProperty("SampleRegistry") {
        fun createDefault() = BufferRegistry(copyAudioFiles = reactiveVariable(true))
    }
}