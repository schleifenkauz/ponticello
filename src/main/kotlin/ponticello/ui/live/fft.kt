package ponticello.ui.live

import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.sin
import kotlin.math.sqrt

fun computeFFT(real: DoubleArray): DoubleArray {
    val n = real.size
    val imag = DoubleArray(n)

    val bits = (ln(n.toDouble()) / ln(2.0)).toInt()
    for (i in 0..<n) {
        val j = Integer.reverse(i) ushr (32 - bits)
        if (j > i) {
            var temp = real[i]
            real[i] = real[j]
            real[j] = temp
            temp = imag[i]
            imag[i] = imag[j]
            imag[j] = temp
        }
    }

    var len = 2
    while (len <= n) {
        val angle = -2 * Math.PI / len
        val wlenCos = cos(angle)
        val wlenSin = sin(angle)

        var i = 0
        while (i < n) {
            var wReal = 1.0
            var wImag = 0.0
            for (j in 0..<len / 2) {
                val even = i + j
                val odd = i + j + len / 2

                val r = real[odd] * wReal - imag[odd] * wImag
                val im = real[odd] * wImag + imag[odd] * wReal

                real[odd] = real[even] - r
                imag[odd] = imag[even] - im
                real[even] += r
                imag[even] += im

                val wTemp = wReal * wlenCos - wImag * wlenSin
                wImag = wReal * wlenSin + wImag * wlenCos
                wReal = wTemp
            }
            i += len
        }
        len = len shl 1
    }

    val magnitudes = DoubleArray(n / 2)
    for (i in 0..<n / 2) {
        magnitudes[i] = sqrt(real[i] * real[i] + imag[i] * imag[i])
    }
    return magnitudes
}

fun applyHannWindow(samples: DoubleArray) {
    for (i in samples.indices) {
        samples[i] *= 0.5 * (1 - cos(2 * Math.PI * i / (samples.size - 1)))
    }
}