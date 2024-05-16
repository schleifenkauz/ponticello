package xenakis.impl

import javax.sound.sampled.AudioInputStream
import kotlin.math.pow

fun AudioInputStream.readChannels(): Array<DoubleArray> {
    val frames = frameLength.toInt()
    val channels = format.channels
    val bytesPerSample = format.sampleSizeInBits / 8
    val maxValue = 2.0.pow(format.sampleSizeInBits - 1)
    val data = Array(channels) { DoubleArray(frames) }
    val buffered = buffered()
    for (i in 0 until frames) {
        for (j in 0 until channels) {
            var intValue = 0
            for (k in 0 until 4) {
                intValue = intValue shl 8
                if (k < bytesPerSample) intValue = intValue or buffered.read()
                else intValue /= 256
            }
            data[j][i] = intValue / maxValue
        }
    }
    buffered.close()
    return data
}