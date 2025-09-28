package ponticello.ui.record

import javafx.application.Platform
import javafx.scene.canvas.Canvas
import javafx.scene.image.Image
import javafx.scene.image.WritableImage
import org.jtransforms.fft.DoubleFFT_1D
import ponticello.impl.*
import kotlin.math.log10
import kotlin.math.roundToInt
import kotlin.math.sqrt

class SpectrogramCanvas(
    private val buffer: AudioBuffer,
    private val regionDuration: Decimal, //duration represented by individual images
    initialDisplayRange: DecimalRange
) : AudioBuffer.Listener, Canvas() {
    private val segments = mutableListOf<SpectrogramSegment>()
    var displayRange: DecimalRange = initialDisplayRange

    private val pixelsPerSecond get() = width / displayRange.dur
    private var repainting = false

    private val fft = DoubleFFT_1D(buffer.bufferSize.toLong())

    fun start() {
        buffer.addListener(this)
        CleanupThread().start()
        RepaintThread().start()
    }

    override fun accept(currentTime: Decimal, samples: DoubleArray) {
        val segmentIdx = (currentTime * regionDuration).toInt()
        val affectedSegment = getSegment(segmentIdx)
        affectedSegment.invalidate()
    }

    private fun getSegment(i: Int): SpectrogramSegment = when {
        i < segments.size -> segments[i]
        i >= segments.size -> {
            for (j in segments.size..i) {
                val range = DecimalRange(j * regionDuration, (j + 1) * regionDuration)
                val segment = SpectrogramSegment(range, null, 0L)
                segments.add(segment)
            }
            segments.last()
        }

        else -> error("Invalid segment index: $i.")
    }

    fun repaint() {
        if (repainting) return
        repainting = true
        val regionWidth = (regionDuration * (pixelsPerSecond)).toDouble()
        val firstRegion = (displayRange.start * regionDuration).toInt()
        val lastRegion = (displayRange.endInclusive * regionDuration).ceilToInt()
        for (i in firstRegion..lastRegion) {
            val segment = getSegment(i)
            val img = segment.getImage()
            val x = (i - displayRange.start) * regionWidth
            val y = 0.0
            val h = this.height
            Platform.runLater {
                graphicsContext2D.drawImage(img, x.toDouble(), y, regionWidth, h)
            }
        }
        repainting = false
    }

    private inner class SpectrogramSegment(
        private val range: DecimalRange,
        private var image: WritableImage?,
        private var lastTouchedMs: Long
    ) {
        fun getImage(): Image {
            lastTouchedMs = System.currentTimeMillis()
            if (image != null) return image!!
            val samples = buffer.read(range)
            val fftSize = buffer.bufferSize
            val hopSize = fftSize / 2
            val frames = (samples.size - fftSize) / hopSize
            val freqBins = fftSize / 2

            val img = WritableImage(frames, freqBins)
            val pw = img.pixelWriter

            val window = hammingWindow(fftSize)
            val buf = DoubleArray(fftSize * 2)

            for (frame in 0 until frames) {
                val offset = frame * hopSize
                for (i in 0 until fftSize) {
                    val v = samples.getOrElse(offset + i) { 0.0 }
                    buf[2 * i] = v * window[i]
                    buf[2 * i + 1] = 0.0
                }
                fft.complexForward(buf)
                for (k in 0 until freqBins) {
                    val re = buf[2 * k]
                    val im = buf[2 * k + 1]
                    val mag = sqrt(re * re + im * im)

                    val db = 20.0 * log10(mag + 1e-6)
                    val norm = ((db - MIN_DB) / (MAX_DB - MIN_DB)).coerceIn(0.0, 1.0)
                    val argb = heatMap(norm)
                    pw.setArgb(frame, k, argb)
                }
            }
            image = img
            return img
        }

        fun invalidate() {
            image = null
        }

        fun clearIfUnused() {
            if (image == null) return
            if (range.endInclusive >= displayRange.start && range.start <= displayRange.endInclusive) return
            if (System.currentTimeMillis() - lastTouchedMs < CLEANUP_THRESHOLD) return
            image = null
        }
    }

    private inner class RepaintThread : Thread("Spectrogram repaint thread") {
        init {
            isDaemon = true
        }

        override fun run() {
            while (!interrupted()) {

                repaint()
                try {
                    val repaintPeriodMs = pixelsPerSecond.toLong()
                    sleep(repaintPeriodMs)
                } catch (e: InterruptedException) {
                    break
                }
            }
        }
    }

    private inner class CleanupThread : Thread("Spectrogram cleanup thread") {
        init {
            isDaemon = true
        }

        override fun run() {
            while (!(interrupted())) {
                for (seg in segments) {
                    seg.clearIfUnused()
                }
                try {
                    sleep(CLEANUP_INTERVAL)
                } catch (e: InterruptedException) {
                    break
                }
            }
        }
    }

    companion object {
        private const val CLEANUP_THRESHOLD = 10000L
        private const val CLEANUP_INTERVAL = 1000L

        private const val MIN_DB = -40.0
        private const val MAX_DB = 0.0

        private fun heatMap(norm: Double): Int {
            val r = ((norm * 3).coerceAtMost(1.0) * 255).roundToInt()
            val g = ((norm * 3 - 1).coerceIn(0.0, 1.0) * 255).roundToInt()
            val b = ((norm * 3 - 2).coerceIn(0.0, 1.0) * 255).roundToInt()
            return 0xFF shl 24 or (r shl 16) or (g shl 8) or b
        }
    }
}