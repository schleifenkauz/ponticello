package ponticello.ui.record

import kotlinx.serialization.Serializable
import ponticello.impl.Decimal
import ponticello.impl.DecimalRange
import ponticello.model.record.LiveBufferObject
import reaktive.value.ReactiveVariable
import reaktive.value.reactiveVariable

@Serializable
sealed class LiveBufferViewConfig {
    abstract fun createBufferView(buffer: LiveBufferObject, displayRange: DecimalRange): LiveAudioBufferView

    @Serializable
    data class Waveform(val yScale: ReactiveVariable<Double>) : LiveBufferViewConfig() {
        override fun createBufferView(buffer: LiveBufferObject, displayRange: DecimalRange): LiveAudioBufferView =
            LiveWaveformView(buffer.peaks, displayRange, this)

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
        override fun createBufferView(buffer: LiveBufferObject, displayRange: DecimalRange): LiveAudioBufferView {
            val view = LiveSpectrogramView(buffer.buffer, framesPerImage = 100, displayRange, this)
            view.start()
            return view
        }
    }
}