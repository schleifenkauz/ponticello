package ponticello.ui.live

class WaveformPeaks(
    val buffer: LiveAudioFileBuffer,
    private val minZoom: Int, private val maxZoom: Int
) {
    private val caches = Array(maxZoom - minZoom + 1) { i ->
        val regionSize = (i + minZoom).pow2()
        WaveformPeakCache(buffer, regionSize)
    }

    fun getPeaks(range: DoubleRange, zoomFactor: Int): Peaks {
        val regionSize = zoomFactor.pow2()
        val nSamples = range.dur * buffer.sampleRate
        val pixels = (nSamples / regionSize).toInt()
        return when {
            zoomFactor < minZoom -> {
                val samples = buffer.read(range)
                val minima = DoubleArray(pixels) { 1.0 }
                val maxima = DoubleArray(pixels) { -1.0 }
                for (p in 0 until pixels) {
                    for (i in p * regionSize until (p + 1) * regionSize) {
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
                val minima = DoubleArray(regionSize) { 1.0 }
                val maxima = DoubleArray(regionSize) { -1.0 }
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

    fun getPeaks(range: DoubleRange, width: Double): Peaks {
        val samplesPerPixel = (range.dur * buffer.sampleRate) / width
        val regionSize = samplesPerPixel.toInt()
        val zoomFactor = (0..31).first { z -> z.pow2() >= regionSize }
        return getPeaks(range, zoomFactor)
    }
}