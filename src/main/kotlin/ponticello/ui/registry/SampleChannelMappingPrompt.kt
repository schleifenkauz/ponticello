package ponticello.ui.registry

import fxutils.controls.IntSpinner
import fxutils.prompt.CompoundPrompt
import ponticello.model.server.SampleObject
import ponticello.ui.record.ChannelMappingGrid
import reaktive.Observer
import reaktive.and
import reaktive.value.now
import reaktive.value.reactiveVariable

class SampleChannelMappingPrompt(
    private val sample: SampleObject
) : CompoundPrompt<Unit>("Channel Mapping", labelWidth = 80.0) {
    private val outputChannels = reactiveVariable(sample.sourceChannels.now)
    private val channelsSpinner = IntSpinner(outputChannels, 1..sample.sourceChannels.now)

    private val mappingGrid = ChannelMappingGrid(sample.sourceChannels, outputChannels)

    private val observer: Observer

    init {
        addItem("Channels: ", channelsSpinner)
        mappingGrid.initializeDefault()
        content.children.add(mappingGrid)
        observer = sample.sourceChannels.observe { _, _, new ->
            if (outputChannels.now == 0) {
                outputChannels.set(new)
            }
            channelsSpinner.setRange(1..new)
        } and outputChannels.observe { _ -> window.sizeToScene() }
    }

    override fun confirm() {
        sample.channelMapping = mappingGrid.getMapping()
    }
}