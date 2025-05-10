package ponticello.model.registry

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.serialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.serializer
import reaktive.value.now
import ponticello.impl.json

abstract class CustomNamedObjectListSerializer<O : NamedObject, C, L : NamedObjectList<O>>(
    private val contentSerializer: KSerializer<C>,
) : KSerializer<L> {
    protected abstract fun createList(elements: MutableList<O>): L

    protected abstract fun getContent(obj: O): C

    protected abstract fun createObject(name: String, content: C): O

    override val descriptor: SerialDescriptor
        get() = serialDescriptor<JsonObject>()

    override fun serialize(encoder: Encoder, value: L) {
        val obj = JsonObject(value.associate { v ->
            val content = getContent(v)
            val value = json.encodeToJsonElement(contentSerializer, content)
            v.name.now to value
        })
        encoder as JsonEncoder
        encoder.encodeJsonElement(obj)
    }

    override fun deserialize(decoder: Decoder): L {
        val obj = decoder.decodeSerializableValue(serializer<JsonObject>())
        val objects = obj.mapTo(mutableListOf()) { (name, value) ->
            val content = json.decodeFromJsonElement(contentSerializer, value)
            createObject(name, content)
        }
        return createList(objects)
    }
}