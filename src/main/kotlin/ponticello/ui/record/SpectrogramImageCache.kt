package ponticello.ui.record

import javafx.scene.canvas.Canvas
import javafx.scene.image.Image
import javafx.scene.image.PixelFormat
import javafx.scene.image.WritableImage
import ponticello.impl.*

class SpectrogramImageCache(
    private val buffer: LiveAudioFileBuffer,
    private val regionSize: Int //number of FFT frames per image
) : LiveAudioFileBuffer.Listener {
    var isActive = false
    private val segments = mutableListOf<SpectrogramSegment>()
    private val fftFramesPerSecond = buffer.sampleRate / buffer.bufferSize
    private val regionsPerSecond = (fftFramesPerSecond * regionSize).toDecimal()
    private var currentRange: DecimalRange = zero..zero

    fun start() {
        buffer.addListener(this)
        CleanupThread().start()
    }

    override fun accept(samples: DoubleArray) {
        if (isActive) {
            val fft = computeFFT(samples)
        }
    }

    fun paint(range: DecimalRange, canvas: Canvas) {
        val pixelsPerFrame = canvas.width / range.dur / fftFramesPerSecond
        val firstRegion = (range.start / regionsPerSecond).toInt()
        val lastRegion = (range.endInclusive / regionsPerSecond).ceilToInt()
        for (i in firstRegion..lastRegion) {
            val segment = segments.getOrNull(i) ?: break
            val img = segment.getImage()
            val sx = if (i.toDecimal() < range.start) (range.start - i) * img.width else 0.0
            var sw = img.width.toDecimal()
            if (i.toDecimal() < range.start) sw -= (range.start - i) * img.width
            if (i + one > range.endInclusive) sw -= (i + 1 - range.endInclusive) * img.width
            val sy = 0.0
            val sh = img.height
            val dx = maxOf(i.toDecimal(), range.start)
            val dw = sw * pixelsPerFrame
            val dy = 0.0
            val dh = canvas.height
            canvas.graphicsContext2D.drawImage(
                img,
                sx.toDouble(), sy, sw.toDouble(), sh,
                dx.toDouble(), dy, dw.toDouble(), dh
            )
        }
        currentRange = range
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
            val bufSize = buffer.bufferSize
            val frames = samples.size / bufSize
            val img = WritableImage(regionSize, bufSize)
            for (f in 0 until frames) {
                val offset = f * bufSize
                val real = DoubleArray(bufSize) { i -> samples[i] + offset }
                val fft = computeFFT(real)
                val pixels = IntArray(bufSize)
                img.pixelWriter.setPixels(f, 0, 1, bufSize, PixelFormat.getIntArgbInstance(), pixels, 0, 1)
            }
            return img
        }

        fun clearIfUnused() {
            if (image == null) return
            if (range.endInclusive >= currentRange.start && range.start <= currentRange.endInclusive) return
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
    }
}