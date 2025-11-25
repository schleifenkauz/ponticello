package ponticello.model.record

import kotlinx.serialization.Serializable
import ponticello.model.registry.ObjectListSerializer
import ponticello.model.registry.ObjectRegistry

@Serializable(with = LiveBufferRegistry.Serializer::class)
class LiveBufferRegistry(override val objects: MutableList<LiveBufferObject>) : ObjectRegistry<LiveBufferObject>() {
    override val objectType: String get() = "LiveBuffer"

    object Serializer : ObjectListSerializer<LiveBufferObject, LiveBufferRegistry>(
        LiveBufferObject.serializer(), ::LiveBufferRegistry
    )

    companion object {
        fun createDefault(): LiveBufferRegistry = LiveBufferRegistry(mutableListOf())
    }
}