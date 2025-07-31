package ponticello.model.flow

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.serialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import ponticello.model.obj.BusReference
import ponticello.model.registry.ObjectList
import reaktive.value.now
import reaktive.value.reactiveVariable

@Serializable
class VSTPluginParameterMappingList(
    override val objects: MutableList<VSTPluginParameterMapping> = mutableListOf(),
) : ObjectList<VSTPluginParameterMapping>() {
    override val objectType: String
        get() = "VST parameter mapping"

    fun copy() = VSTPluginParameterMappingList(objects.mapTo(mutableListOf()) { mapping -> mapping.copy() })

    object Serializer : KSerializer<VSTPluginParameterMappingList> {
        override val descriptor: SerialDescriptor
            get() = serialDescriptor<Map<String, BusReference>>()

        override fun deserialize(decoder: Decoder): VSTPluginParameterMappingList {
            val map: Map<String, BusReference> = decoder.decodeSerializableValue(kotlinx.serialization.serializer())
            val list = map.entries.mapTo(mutableListOf()) { (name, bus) ->
                VSTPluginParameterMapping(name, reactiveVariable(bus))
            }
            return VSTPluginParameterMappingList(list)
        }

        override fun serialize(encoder: Encoder, value: VSTPluginParameterMappingList) {
            val map = value.associate { mapping -> mapping.name to mapping.controlBus.now }
            encoder.encodeSerializableValue(kotlinx.serialization.serializer(), map)
        }
    }
}