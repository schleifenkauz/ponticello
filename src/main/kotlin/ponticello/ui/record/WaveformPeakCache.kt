package ponticello.ui.record

import ponticello.impl.DecimalRange
import ponticello.impl.div
import ponticello.impl.times

class WaveformPeakCache(
    private val buffer: AudioBuffer,
    val regionSize: Int
) : AudioBuffer.Listener {
    private val max = ArrayList<Double>(MAX_INITIAL_CAPACITY / regionSize)
    private val min = ArrayList<Double>(MAX_INITIAL_CAPACITY / regionSize)
    private var acceptedSamples = 0
    private var currentMin = 1.0
    private var currentMax = -1.0

    init {
        buffer.addListener(this)
    }

    @Synchronized
    override fun accept(sampleOffset: Long, samples: DoubleArray) {
        if (samples.size >= regionSize) {
            for (i in samples.indices step regionSize) {
                currentMin = 1.0
                currentMax = -1.0
                for (j in i until i + regionSize) {
                    val v = samples[j]
                    if (v > currentMax) currentMax = v
                    if (v < currentMin) currentMin = v
                }
                max.add(currentMax)
                min.add(currentMin)
            }
        } else {
            for (v in samples) {
                if (v > currentMax) currentMax = v
                if (v < currentMin) currentMin = v
            }
            acceptedSamples += samples.size
            if (acceptedSamples == regionSize) {
                max.add(currentMax)
                min.add(currentMin)
                currentMin = 1.0
                currentMax = -1.0
                acceptedSamples = 0
            }
        }
    }

    @Synchronized
    fun getPeaks(range: DecimalRange): Peaks {
        val from = (buffer.sampleRate * range.start / regionSize).toInt()
        val to = (range.endInclusive * buffer.sampleRate / regionSize).toInt()
        val minima = min.subList(from, to.coerceAtMost(min.size)).toList()
        val maxima = max.subList(from, to.coerceAtMost(min.size)).toList()
        val size = to - from
        return Peaks(size, minima, maxima)
    }

    companion object {
        private const val MAX_INITIAL_CAPACITY = 1024 * 1024
    }
}

