package xenakis.impl

import javafx.scene.paint.Color
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.serialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

object ColorSerializer : KSerializer<Color> {
    override val descriptor: SerialDescriptor = serialDescriptor<String>()

    override fun serialize(encoder: Encoder, value: Color) {
        encoder.encodeString(value.toString())
    }

    override fun deserialize(decoder: Decoder): Color {
        return Color.web(decoder.decodeString())
    }
}