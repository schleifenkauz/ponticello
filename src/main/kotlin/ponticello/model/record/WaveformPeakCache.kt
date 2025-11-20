package ponticello.model.record

import ponticello.impl.DecimalRange
import ponticello.impl.div
import ponticello.impl.times

class WaveformPeakCache(
    private val buffer: AudioBuffer,
    val regionSize: Int
) : AudioBuffer.Listener {
    private val max = ArrayList<Float>(MAX_INITIAL_CAPACITY / regionSize)
    private val min = ArrayList<Float>(MAX_INITIAL_CAPACITY / regionSize)
    private var acceptedSamples = 0
    private var currentMin = 1.0f
    private var currentMax = -1.0f

    init {
        buffer.addListener(this)
    }

    @Synchronized
    override fun accept(sampleOffset: Long, samples: FloatArray, frames: Int) {
        if (frames >= regionSize) {
            for (i in 0 until frames step regionSize) {
                currentMin = 1.0f
                currentMax = -1.0f
                for (j in i until i + regionSize) {
                    val v = samples[j]
                    if (v > currentMax) currentMax = v
                    if (v < currentMin) currentMin = v
                }
                max.add(currentMax)
                min.add(currentMin)
            }
        } else {
            for (i in 0 until frames) {
                val v = samples[i]
                if (v > currentMax) currentMax = v
                if (v < currentMin) currentMin = v
            }
            acceptedSamples += samples.size
            if (acceptedSamples == regionSize) {
                max.add(currentMax)
                min.add(currentMin)
                currentMin = 1.0f
                currentMax = -1.0f
                acceptedSamples = 0
            }
        }
    }

    override fun onClear() {
        min.clear()
        max.clear()
        acceptedSamples = 0
        currentMin = 1.0f
        currentMax = -1.0f
    }

    @Synchronized
    fun getPeaks(range: DecimalRange): Peaks {
        val from = (buffer.sampleRate * range.start / regionSize).toInt()
        val to = (range.endInclusive * buffer.sampleRate / regionSize).toInt()
        if (from >= to || from >= min.size || to < 0) return Peaks(0, emptyList(), emptyList())
        val minima = min.subList(from.coerceAtLeast(0), to.coerceAtMost(min.size)).toList()
        val maxima = max.subList(from.coerceAtLeast(0), to.coerceAtMost(min.size)).toList()
        val size = to - from
        return Peaks(size, minima, maxima)
    }

    companion object {
        private const val MAX_INITIAL_CAPACITY = 1024 * 1024
    }
}

