package xenakis.model

import hextant.context.Context
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.serialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import reaktive.value.now

interface ObjectReference<O : NamedObject> {
    fun get(): O

    fun initialize(context: Context)

    abstract class Serializer<R : ObjectReference<*>> : KSerializer<R> {
        override val descriptor: SerialDescriptor
            get() = serialDescriptor<String>()

        abstract fun createReference(name: String): R

        override fun serialize(encoder: Encoder, value: R) {
            encoder.encodeString(value.get().name.now)
        }

        override fun deserialize(decoder: Decoder): R {
            val name = decoder.decodeString()
            return createReference(name)
        }
    }
}