package ponticello.ui.record

import kotlinx.serialization.Serializable
import ponticello.impl.Decimal
import ponticello.impl.DecimalRange
import ponticello.model.record.AudioBuffer
import reaktive.value.ReactiveVariable
import reaktive.value.reactiveVariable

@Serializable
sealed class LiveBufferViewConfig {
    abstract fun createBufferCanvas(buffer: AudioBuffer, displayRange: DecimalRange): LiveAudioBufferCanvas

    @Serializable
    data class Waveform(val yScale: ReactiveVariable<Double>) : LiveBufferViewConfig() {
        override fun createBufferCanvas(buffer: AudioBuffer, displayRange: DecimalRange): LiveAudioBufferCanvas =
//            LiveWaveformCanvas(WaveformPeaks.get(buffer), displayRange, this)
            SimpleLiveWaveformCanvas(buffer, displayRange)

        companion object {
            fun default() = Waveform(yScale = reactiveVariable(1.0))
        }
    }

    @Serializable
    data class Spectrogram(
        val noiseFloorDb: ReactiveVariable<Double>,
        val cleanupPeriod: ReactiveVariable<Decimal>,
        val cleanupThreshold: ReactiveVariable<Decimal>
    ) : LiveBufferViewConfig() {
        override fun createBufferCanvas(buffer: AudioBuffer, displayRange: DecimalRange): LiveAudioBufferCanvas {
            val view = LiveSpectrogramCanvas(buffer, framesPerImage = 100, displayRange, this)
            view.start()
            return view
        }
    }
}