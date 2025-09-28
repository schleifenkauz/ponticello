package ponticello.ui.record

import javafx.application.Platform
import javafx.scene.canvas.Canvas
import javafx.scene.image.PixelWriter
import javafx.scene.image.WritableImage
import org.jtransforms.fft.DoubleFFT_1D
import ponticello.impl.*
import java.awt.Color
import kotlin.math.log10
import kotlin.math.sqrt

class SpectrogramCanvas(
    private val buffer: AudioBuffer,
    private val framesPerImage: Int,
    initialDisplayRange: DecimalRange
) : AudioBuffer.Listener, Canvas() {
    private val segments = mutableListOf<SpectrogramSegment>()
    var displayRange: DecimalRange = initialDisplayRange

    private val fftSize = buffer.bufferSize
    private val hopSize = fftSize / 2
    private val freqBins = fftSize / 2
    private val fft = DoubleFFT_1D(fftSize.toLong())
    private val hammingWindow = hammingWindow(fftSize)
    private val fftBuf = DoubleArray(fftSize * 2)

    private val regionDuration = ((hopSize * framesPerImage) / buffer.sampleRate).toDecimal()
    private val regionWidth get() = (regionDuration * (width / displayRange.dur)).toDouble()

    private var lastAcceptedSamples = DoubleArray(fftSize)

    private var repainting = false

    fun start() {
        buffer.addListener(this)
        CleanupThread().start()
    }

    override fun accept(sampleOffset: Long, samples: DoubleArray) {
        var frameIndex = (sampleOffset / hopSize).toInt()
        val segmentIdx = frameIndex / framesPerImage
        frameIndex %= framesPerImage
        val affectedSegment = getSegment(segmentIdx)
        if (!affectedSegment.isInDisplayRange()) return
        val img = affectedSegment.getImage()

        fftBuf.prepareBufForFFT(lastAcceptedSamples, srcOffset = hopSize, dstOffset = 0, len = hopSize)
        fftBuf.prepareBufForFFT(samples, srcOffset = 0, dstOffset = hopSize, len = hopSize)
        fft.complexForward(fftBuf)
        img.pixelWriter.writeFrame(fftBuf, frameIndex)

        fftBuf.prepareBufForFFT(samples, srcOffset = 0, len = fftSize)
        fft.complexForward(fftBuf)
        img.pixelWriter.writeFrame(fftBuf, frameIndex + 1)

        drawSegment(segmentIdx)
        lastAcceptedSamples = samples
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
        val firstRegion = (displayRange.start * regionDuration).toInt()
        val lastRegion = (displayRange.endInclusive * regionDuration).ceilToInt()
        for (i in firstRegion..lastRegion) {
            drawSegment(i)
        }
        repainting = false
    }

    private fun drawSegment(i: Int) {
        val segment = getSegment(i)
        val img = segment.getImage()
        val x = (i - displayRange.start) * regionWidth
        val y = 0.0
        val h = this.height
        Platform.runLater {
            graphicsContext2D.drawImage(img, x.toDouble(), y, regionWidth, h)
        }
    }

    private fun DoubleArray.prepareBufForFFT(
        src: DoubleArray, srcOffset: Int = 0,
        dstOffset: Int = 0, len: Int = src.size
    ) {
        for (i in 0 until len) {
            val v = src.getOrElse(srcOffset + i) { 0.0 }
            this[2 * (i + dstOffset)] = v * hammingWindow[i + dstOffset]
            this[2 * (i + dstOffset) + 1] = 0.0
        }
    }

    private fun PixelWriter.writeFrame(buf: DoubleArray, x: Int) {
        for (y in 0 until freqBins) {
            val re = buf[2 * y]
            val im = buf[2 * y + 1]
            val mag = sqrt(re * re + im * im)
            val db = 20.0 * log10(mag + 1e-6)
            val norm = ((db - MIN_DB) / (MAX_DB - MIN_DB)).coerceIn(0.0, 1.0).toFloat()
            val color = Color.getHSBColor(0.66f - norm * 0.66f, 1.0f, norm)
            setArgb(x, freqBins - 1 - y, color.rgb)
        }
    }

    private inner class SpectrogramSegment(
        private val range: DecimalRange,
        private var image: WritableImage?,
        private var lastTouchedMs: Long
    ) {
        fun getImage(): WritableImage {
            lastTouchedMs = System.currentTimeMillis()
            if (image != null) return image!!
            val samples = buffer.read(range)
            val frames = (samples.size - fftSize) / hopSize
            val img = WritableImage(framesPerImage, freqBins)
            val pw = img.pixelWriter

            val buf = DoubleArray(fftSize * 2)
            for (frame in 0 until frames) {
                val srcOffset = frame * hopSize
                if (srcOffset >= samples.size) break
                buf.prepareBufForFFT(samples, srcOffset, dstOffset = 0, len = fftSize)
                fft.complexForward(buf)
                pw.writeFrame(buf, frame)
            }
            image = img
            return img
        }

        fun isInDisplayRange() =
            range.endInclusive >= displayRange.start && range.start <= displayRange.endInclusive

        fun clearIfUnused() {
            if (image == null) return
            if (isInDisplayRange()) return
            if (System.currentTimeMillis() - lastTouchedMs < CLEANUP_THRESHOLD) return
            image = null
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

        private const val MIN_DB = -50.0
        private const val MAX_DB = 0.0
    }
}