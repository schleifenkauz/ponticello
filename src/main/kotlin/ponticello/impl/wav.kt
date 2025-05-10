package ponticello.impl

import java.io.InputStream
import javax.sound.sampled.AudioInputStream
import kotlin.math.pow

fun AudioInputStream.readChannels(): Array<DoubleArray> {
    val frames = frameLength.toInt()
    val channels = format.channels
    val bytesPerSample = bytesPerSample()
    val maxValue = intMaxValue()
    val data = Array(channels) { DoubleArray(frames) }
    val buffered = buffered()
    for (i in 0 until frames) {
        for (j in 0 until channels) {
            val value = buffered.readSample(bytesPerSample, maxValue)
            data[j][i] = value
        }
    }
    buffered.close()
    return data
}

fun AudioInputStream.readStream(): Sequence<DoubleArray> = sequence {
    val buffered = buffered()
    val bytesPerSample = bytesPerSample()
    val channels = format.channels
    val bytesPerFrame = bytesPerSample * channels
    val maxValue = intMaxValue()
    while (true) {
        val available = buffered.available()
        if (available > 0) {
            check(available % bytesPerFrame == 0)
            repeat(available / bytesPerFrame) {
                val frame = DoubleArray(channels) { buffered.readSample(bytesPerSample, maxValue) }
                yield(frame)
            }
        } else {
            Thread.sleep(10)
        }
    }
}

private fun AudioInputStream.bytesPerSample() = format.sampleSizeInBits / 8

private fun AudioInputStream.intMaxValue() = 2.0.pow(format.sampleSizeInBits - 1)

private fun InputStream.readSample(bytesPerSample: Int, maxValue: Double): Double {
    var intValue = 0
    for (k in 0 until 4) {
        intValue = intValue shl 8
        if (k < bytesPerSample) intValue = intValue or read()
        else intValue /= 256
    }
    return intValue / maxValue
}
