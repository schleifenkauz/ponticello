package ponticello.ui.record

import fxutils.prompt.SimpleSelectorPrompt
import hextant.context.Context
import javafx.scene.control.Label
import javafx.scene.layout.Region
import ponticello.model.record.CaptureSource
import ponticello.sc.client.SuperColliderClient

class AudioSourceSelectorPrompt(context: Context) : SimpleSelectorPrompt<CaptureSource>(
    getOptions(context), "Select audio input device"
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
        private fun getOptions(context: Context): List<CaptureSource.Mixer> {
            val sampleRate = context[SuperColliderClient].sampleRate
            return CaptureSource.Mixer.getAvailable(sampleRate)
        }
    }
}