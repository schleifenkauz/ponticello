package ponticello.ui.record

import fxutils.prompt.SimpleSelectorPrompt
import hextant.context.Context
import javafx.scene.control.Label
import javafx.scene.layout.Region
import ponticello.model.obj.project
import ponticello.model.project.LIVE_BUFFERS
import ponticello.model.project.get
import ponticello.model.record.CaptureSource
import ponticello.model.record.LiveBufferRegistry

class AudioSourceSelectorPrompt(registry: LiveBufferRegistry) : SimpleSelectorPrompt<CaptureSource>(
    getOptions(registry), "Select audio input device"
) {
    override fun createCell(option: CaptureSource): Region =
        when (option) {
            is CaptureSource.None -> Label("<none>")
            is CaptureSource.Mixer -> Label("hw: ${option.name}")
            is CaptureSource.Jack -> Label("JACK: ${option.name}")
        }

    override fun extractText(option: CaptureSource): String = when (option) {
        CaptureSource.None -> "<none>"
        is CaptureSource.Mixer -> option.name
        is CaptureSource.Jack -> option.name
    }

    companion object {
        private fun getOptions(registry: LiveBufferRegistry): List<CaptureSource> {
//            val sampleRate = context[SuperColliderClient].sampleRate
//            return CaptureSource.Mixer.getAvailableSources(sampleRate)
            val takenSources = registry.map { obj -> obj.source }.toSet()
            return CaptureSource.Jack.getAvailableSources() - takenSources
        }
    }
}