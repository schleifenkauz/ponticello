package ponticello.model.record

import ponticello.impl.*
import java.util.*

class WaveformPeaks private constructor(
    val buffer: AudioBuffer,
    private val minZoom: Int, private val maxZoom: Int
) {
    private val caches = Array(maxZoom - minZoom + 1) { i ->
        val regionSize = (i + minZoom).pow2()
        WaveformPeakCache(buffer, regionSize)
    }

    fun getPeaks(range: DecimalRange, zoomFactor: Int): Peaks {
        val regionSize = zoomFactor.pow2()
        val nSamples = range.duration * buffer.sampleRate
        val pixels = (nSamples / regionSize).toInt()
        return when {
            zoomFactor < minZoom -> {
                val samples = buffer.read(range)
                val minima = FloatArray(pixels) { 1.0f }
                val maxima = FloatArray(pixels) { -1.0f }
                for (p in 0 until pixels) {
                    for (i in p * regionSize until ((p + 1) * regionSize).coerceAtMost(samples.size)) {
                        val v = samples[i]
                        if (v < minima[p]) minima[p] = v
                        else if (v > maxima[p]) maxima[p] = v
                    }
                }
                Peaks(pixels, minima.asList(), maxima.asList())
            }

            zoomFactor > maxZoom -> {
                val cache = caches.last()
                val peaks = cache.getPeaks(range)
                val minima = FloatArray(regionSize) { 1.0f }
                val maxima = FloatArray(regionSize) { -1.0f }
                val n = regionSize / cache.regionSize
                for (p in 0 until pixels) {
                    for (i in p * n until (p + 1) * n) {
                        val min = peaks.getMin(i)
                        val max = peaks.getMin(i)
                        if (min < minima[p]) minima[p] = min
                        if (max > maxima[p]) maxima[p] = max
                    }
                }
                Peaks(pixels, minima.asList(), maxima.asList())
            }

            else -> caches[zoomFactor - minZoom].getPeaks(range)
        }
    }

    fun getPeaks(range: DecimalRange, width: Double): Peaks {
        val samplesPerPixel = (range.duration * buffer.sampleRate) / width
        val regionSize = samplesPerPixel.toInt()
        val zoomFactor = (0..31).first { z -> z.pow2() >= regionSize }
        return getPeaks(range, zoomFactor)
    }

    companion object {
        private val cache = WeakHashMap<AudioBuffer, WaveformPeaks>()

        private const val MIN_ZOOM = 4
        private const val MAX_ZOOM = 16

        fun get(buffer: AudioBuffer): WaveformPeaks =
            cache.getOrPut(buffer) { WaveformPeaks(buffer, MIN_ZOOM, MAX_ZOOM) }
    }
}