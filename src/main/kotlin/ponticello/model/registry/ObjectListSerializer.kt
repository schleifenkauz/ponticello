package ponticello.model.registry

import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.listSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import ponticello.model.obj.ContextualObject

open class ObjectListSerializer<O : ContextualObject, L : ObjectList<O>>(
    val elementSerializer: KSerializer<O>,
    val createList: (MutableList<O>) -> L
) : KSerializer<L> {
    override val descriptor: SerialDescriptor
        get() = listSerialDescriptor(elementSerializer.descriptor)

    override fun serialize(encoder: Encoder, value: L) {
        encoder.encodeSerializableValue(ListSerializer(elementSerializer), value as List<O>)
    }

    override fun deserialize(decoder: Decoder): L {
        val list = decoder.decodeSerializableValue(ListSerializer(elementSerializer))
        return createList(list.toMutableList())
    }
}