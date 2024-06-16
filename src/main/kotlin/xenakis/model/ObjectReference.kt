@file:OptIn(ExperimentalSerializationApi::class)

package xenakis.model

import hextant.context.Context
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.serialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import reaktive.value.now

interface ObjectReference<O : NamedObject> {
    fun get(): O

    fun resolve(context: Context)

    abstract class Serializer<R : ObjectReference<*>?> : KSerializer<R> {
        override val descriptor: SerialDescriptor
            get() = serialDescriptor<String?>()

        abstract fun createReference(name: String): R

        override fun serialize(encoder: Encoder, value: R) {
            if (value == null) encoder.encodeNull()
            else {
                encoder.encodeNotNullMark()
                encoder.encodeString(value.get().name.now)
            }
        }

        override fun deserialize(decoder: Decoder): R {
            return if (decoder.decodeNotNullMark()) createReference(decoder.decodeString())
            else null as R
        }
    }
}