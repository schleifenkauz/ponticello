package ponticello.ui.record

import fxutils.prompt.SimpleSelectorPrompt
import javafx.scene.control.Label
import javafx.scene.layout.Region
import ponticello.model.record.CaptureSource
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.TargetDataLine

class AudioSourceSelectorPrompt(format: AudioFormat) : SimpleSelectorPrompt<CaptureSource>(
    getOptions(format), "Select audio input device"
) {
    override fun createCell(option: CaptureSource): Region =
        when (option) {
            is CaptureSource.None -> Label("<none>")
            is CaptureSource.Mixer -> Label(option.name)
        }

    override fun extractText(option: CaptureSource): String = when (option) {
        CaptureSource.None -> "<none>"
        is CaptureSource.Mixer -> option.name
    }

    companion object {
        private fun getOptions(format: AudioFormat) = AudioSystem.getMixerInfo().filter { info ->
            val mixer = AudioSystem.getMixer(info)
            mixer != null && mixer.isLineSupported(DataLine.Info(TargetDataLine::class.java, format))
        }.map { info -> CaptureSource.Mixer(info.name, bufferSize = 1024, channels = 1) }
    }
}