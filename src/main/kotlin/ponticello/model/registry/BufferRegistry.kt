package ponticello.model.registry

import bundles.PublicProperty
import bundles.publicProperty
import bundles.set
import com.illposed.osc.OSCMessageEvent
import com.illposed.osc.OSCMessageListener
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
import ponticello.impl.Logger
import ponticello.impl.json
import ponticello.impl.toDecimal
import ponticello.model.obj.BufferObject
import ponticello.model.obj.SampleObject
import ponticello.model.obj.SuperColliderObject
import ponticello.model.obj.withName
import ponticello.sc.Identifier
import ponticello.sc.client.SuperColliderClient
import ponticello.sc.client.getArgument
import reaktive.Observer
import reaktive.value.ReactiveVariable
import reaktive.value.now
import reaktive.value.reactiveVariable
import java.io.File

@Serializable(with = BufferRegistry.Serializer::class)
class BufferRegistry(
    override val objects: MutableList<BufferObject> = mutableListOf(),
    val copyAudioFiles: ReactiveVariable<Boolean>,
) : SuperColliderObjectRegistry<BufferObject>(), OSCMessageListener {
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
        context[SuperColliderClient].addListener(this)
    }

    fun getSample(file: File): SampleObject? = filterIsInstance<SampleObject>().find { o ->
        o.audioFile == file || o.referencedFile() == file
    }

    fun getOrAdd(file: File): SampleObject = getSample(file) ?: run {
        val name = Identifier.truncate(file.nameWithoutExtension)
        val sample = SampleObject.create(name, file)
        add(sample)
        return sample
    }

    override fun acceptMessage(event: OSCMessageEvent) {
        if (event.message.address == "/buffer_info") {
            val name = event.message.getArgument<String>(0, "buffer_name") ?: return
            val duration = event.message.getArgument<Float>(1, "duration")?.toDouble()?.toDecimal() ?: return
            val channels = event.message.getArgument<Int>(2, "channels") ?: return
            val sampleRate = event.message.getArgument<Float>(3, "sampleRate")?.toDouble() ?: return
            val buf = getOrNull(name)
            if (buf == null) {
                Logger.warn("Received buffer_info message: Buffer '$name' not found.", Logger.Category.Buffers)
                return

            }
            if (buf !is SampleObject) {
                Logger.warn("Received buffer_info message: Buffer '$name' is not a sample.", Logger.Category.Buffers)
                return
            }
            buf.updateInfos(duration, channels, sampleRate)
        }
    }

    object Serializer : KSerializer<BufferRegistry> {
        override val descriptor: SerialDescriptor
            get() = serialDescriptor<JsonObject>()

        override fun serialize(encoder: Encoder, value: BufferRegistry) {
            val obj = buildJsonObject {
                put("copyAudioFiles", JsonPrimitive(value.copyAudioFiles.now))
                for (buf in value.objects) {
                    val content = json.encodeToJsonElement(buf)
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
                val buf = when {
                    content is JsonPrimitive -> //for compatibility with older projects
                        SampleObject(reactiveVariable(content.string))
                    else -> json.decodeFromJsonElement<BufferObject>(content)
                }
                objects.add(buf.withName(name))
            }
            return BufferRegistry(objects, reactiveVariable(copyAudioFiles))
        }
    }

    companion object : PublicProperty<BufferRegistry> by publicProperty("SampleRegistry") {
        fun createDefault() = BufferRegistry(copyAudioFiles = reactiveVariable(false))
    }
}