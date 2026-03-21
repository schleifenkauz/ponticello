package ponticello.model.record

import com.illposed.osc.OSCMessage
import hextant.context.Context
import kotlinx.serialization.Serializable
import ponticello.impl.Logger
import ponticello.model.registry.IdProvider
import ponticello.model.registry.ObjectListSerializer
import ponticello.model.registry.ObjectRegistry
import ponticello.sc.client.SuperColliderClient
import ponticello.sc.client.getArgument

@Serializable(with = LiveBufferRegistry.Serializer::class)
class LiveBufferRegistry(override val objects: MutableList<LiveBufferObject>) : ObjectRegistry<LiveBufferObject>() {
    override val objectType: String get() = "LiveBuffer"

    val ids = IdProvider(this)

    override fun initialize(context: Context) {
        super.initialize(context)
        val client = context[SuperColliderClient]
        client.addListener("/toggle_recording") { _, msg -> toggleRecording(msg) }
    }

    private fun toggleRecording(msg: OSCMessage) {
        val bufferId = msg.getArgument<Int>(0, "Buffer ID") ?: return
        val buffer = ids.getById(bufferId)
        if (buffer == null) {
            Logger.warn("Received /toggle_recording for unknown buffer ID: $bufferId", Logger.Category.OSC)
            return
        }
        buffer.toggleEnabled()
    }

    object Serializer : ObjectListSerializer<LiveBufferObject, LiveBufferRegistry>(
        LiveBufferObject.serializer(), ::LiveBufferRegistry
    )

    companion object {
        fun createDefault(): LiveBufferRegistry = LiveBufferRegistry(mutableListOf())
    }
}