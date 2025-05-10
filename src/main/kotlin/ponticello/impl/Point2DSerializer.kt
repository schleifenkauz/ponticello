package ponticello.impl

import javafx.geometry.Point2D
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.serialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encoding.decodeStructure
import kotlinx.serialization.encoding.encodeStructure

object Point2DSerializer : KSerializer<Point2D> {
    override val descriptor: SerialDescriptor
        get() = buildClassSerialDescriptor("Point2D") {
            element("x", serialDescriptor<Double>())
            element("y", serialDescriptor<Double>())
        }

    override fun deserialize(decoder: Decoder): Point2D = decoder.decodeStructure(descriptor) {
        var x = 0.0
        var y = 0.0
        while (true) {
            when (decodeElementIndex(descriptor)) {
                0 -> x = decodeDoubleElement(descriptor, 0)
                1 -> y = decodeDoubleElement(descriptor, 1)
                else -> break
            }
        }
        Point2D(x, y)
    }

    override fun serialize(encoder: Encoder, value: Point2D) = encoder.encodeStructure(descriptor) {
        encodeDoubleElement(descriptor, 0, value.x)
        encodeDoubleElement(descriptor, 1, value.y)
    }
}