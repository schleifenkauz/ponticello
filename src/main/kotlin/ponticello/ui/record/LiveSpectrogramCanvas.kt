package ponticello.ui.record

import javafx.application.Platform
import javafx.scene.image.PixelWriter
import javafx.scene.image.WritableImage
import javafx.scene.paint.Color.BLACK
import org.jtransforms.fft.FloatFFT_1D
import ponticello.impl.*
import ponticello.model.record.AudioBuffer
import reaktive.Observer
import reaktive.value.now
import java.awt.Color
import java.util.concurrent.Executors
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.sqrt

class LiveSpectrogramCanvas(
    private val buffer: AudioBuffer,
    private val framesPerImage: Int,
    initialDisplayRange: DecimalRange,
    private val config: LiveBufferViewConfig.Spectrogram
) : AudioBuffer.Listener, LiveAudioBufferCanvas(initialDisplayRange) {
    private val segments = mutableListOf<SpectrogramSegment>()

    private val fftSize = buffer.bufferSize
    private val hopSize = fftSize / 2
    private val freqBins = fftSize / 2
    private val fft = FloatFFT_1D(fftSize.toLong())
    private val hammingWindow = hammingWindow(fftSize)
    private val fftBuf = FloatArray(fftSize * 2)

    private val regionDuration = ((hopSize * framesPerImage) / buffer.sampleRate).toDecimal()
    private val regionWidth get() = (regionDuration * (width / displayRange.dur)).toDouble()

    private var lastAcceptedSamples = FloatArray(fftSize)

    private lateinit var configObserver: Observer

    fun start() {
        buffer.addListener(this)
        CleanupThread().start()
        configObserver = config.noiseFloorDb.observe { _, _, _ ->
            synchronized(segments) {
                for (seg in segments) {
                    seg.invalidate()
                }
            }
            repaint()
        }
    }

    override fun accept(sampleOffset: Long, samples: FloatArray) = executor.execute {
        var frameIndex = (sampleOffset / hopSize).toInt()
        val segmentIdx = frameIndex / framesPerImage
        frameIndex %= framesPerImage
        val affectedSegment = getSegment(segmentIdx)
        if (!affectedSegment.isInDisplayRange()) return@execute
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
            synchronized(segments) {
                for (j in segments.size..i) {
                    val range = DecimalRange(j * regionDuration, (j + 1) * regionDuration)
                    val segment = SpectrogramSegment(range, null, 0L)
                    segments.add(segment)
                }
            }
            segments.last()
        }

        else -> error("Invalid segment index: $i.")
    }

    override fun repaint() {
        Platform.runLater {
            graphicsContext2D.fill = BLACK
            graphicsContext2D.fillRect(0.0, 0.0, width, height)
        }
        executor.execute {
            val firstRegion = (displayRange.start * regionDuration).toInt()
            val lastRegion = (displayRange.endInclusive * regionDuration).ceilToInt()
            for (i in firstRegion..lastRegion) {
                drawSegment(i)
            }
        }
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

    private fun FloatArray.prepareBufForFFT(
        src: FloatArray, srcOffset: Int = 0,
        dstOffset: Int = 0, len: Int = src.size
    ) {
        for (i in 0 until len) {
            val v = src.getOrElse(srcOffset + i) { 0.0f }
            this[2 * (i + dstOffset)] = v * hammingWindow[i + dstOffset]
            this[2 * (i + dstOffset) + 1] = 0.0f
        }
    }

    private fun PixelWriter.writeFrame(buf: FloatArray, x: Int) {
        for (y in 0 until freqBins) {
            val re = buf[2 * y]
            val im = buf[2 * y + 1]
            val mag = sqrt(re * re + im * im)
            val db = 20.0 * log10(mag + 1e-6)
            val noiseFloor = config.noiseFloorDb.now
            val norm = ((db - noiseFloor) / (-noiseFloor)).coerceIn(0.0, 1.0).toFloat()
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

            val buf = FloatArray(fftSize * 2)
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

        fun invalidate() {
            image = null
        }

        fun clearIfUnused() {
            if (image == null) return
            if (isInDisplayRange()) return
            val cleanupThreshold = (config.cleanupThreshold.now * 1000).toLong()
            if (System.currentTimeMillis() - lastTouchedMs < cleanupThreshold) return
            image = null
        }
    }

    private inner class CleanupThread : Thread("Spectrogram cleanup thread") {
        init {
            isDaemon = true
        }

        override fun run() {
            while (!(interrupted())) {
                synchronized(segments) {
                    for (seg in segments) {
                        seg.clearIfUnused()
                    }
                }
                try {
                    val cleanupPeriodSeconds = config.cleanupPeriod.now
                    sleep((cleanupPeriodSeconds * 1000).toLong())
                } catch (e: InterruptedException) {
                    break
                }
            }
        }
    }

    companion object {
        private val executor =
            Executors.newSingleThreadExecutor { r ->
                val thread = Thread(r, "Spectrogram view thread")
                thread.isDaemon = true
                thread
            }

        fun hammingWindow(n: Int): FloatArray =
            FloatArray(n) { i -> 0.54f - 0.46f * cos(2 * Math.PI * i / (n - 1)).toFloat() }
    }
}