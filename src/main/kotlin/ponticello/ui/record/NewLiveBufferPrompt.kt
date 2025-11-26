package ponticello.ui.record

import fxutils.controls.IntSpinner
import fxutils.prompt.CompoundPrompt
import fxutils.styleClass
import javafx.scene.control.TextField
import ponticello.model.obj.withName
import ponticello.model.record.CaptureSource
import ponticello.model.record.ChannelConfiguration
import ponticello.model.record.LiveBufferObject
import ponticello.model.record.LiveBufferRegistry
import ponticello.sc.Identifier
import reaktive.Observer
import reaktive.value.ReactiveVariable
import reaktive.value.binding.equalTo
import reaktive.value.binding.map
import reaktive.value.binding.or
import reaktive.value.fx.asObservableValue
import reaktive.value.fx.asReactiveValue
import reaktive.value.now
import reaktive.value.reactiveVariable

class NewLiveBufferPrompt(registry: LiveBufferRegistry) : CompoundPrompt<LiveBufferObject>("Add LiveBuffer") {
    private val nameField = TextField() styleClass "sleek-text-field"
    private val source: ReactiveVariable<CaptureSource> = reactiveVariable(CaptureSource.None)
    private val outputChannels = reactiveVariable(0)
    private val channelsSpinner = IntSpinner(outputChannels, 0..0)
    private val sourceSelector = AudioSourceSelectorPrompt(registry).selectorButton(source)

    private val mappingGrid = ChannelMappingGrid(source.map { src -> src.channels }, outputChannels)

    private val sourceObserver: Observer
    private val channelsObserver: Observer

    init {
        content.prefWidth = 300.0
        content.spacing = 5.0

        addItem("Source: ", sourceSelector)
        addItem("Channels: ", channelsSpinner)
        channelsSpinner.isDisable = true
        content.children.add(mappingGrid)
        addItem("Name: ", nameField)

        sourceObserver = source.observe { _, _, src ->
            when (src) {
                CaptureSource.None -> {
                    channelsSpinner.isDisable = true
                    mappingGrid.isManaged = false
                }

                else -> {
                    channelsSpinner.isDisable = false
                    if (outputChannels.now == 0) {
                        outputChannels.set(src.channels)
                    }
                    channelsSpinner.setRange(1..src.channels)
                    mappingGrid.isManaged = true
                    nameField.text = Identifier.truncate(src.name)
                }
            }
            window.sizeToScene()
        }
        channelsObserver = outputChannels.observe { _ -> window.sizeToScene() }

        val nameInvalid = nameField.textProperty().asReactiveValue().map { name -> !Identifier.isValid(name) }
        val noSource = source.equalTo(CaptureSource.None)
        confirmButton.disableProperty().bind(nameInvalid.or(noSource).asObservableValue())
    }

    override fun confirm(): LiveBufferObject? {
        val name = nameField.text.takeIf { Identifier.isValid(it) } ?: return null
        if (source.now is CaptureSource.None) return null
        val viewConfig = LiveBufferViewConfig.Waveform.default()
        val channelConfig = ChannelConfiguration(
            source.now.channels, outputChannels.now,
            mappingGrid.getMapping()
        )
        val enabled = reactiveVariable(true)
        return LiveBufferObject(source.now, channelConfig, viewConfig, enabled).withName(name)
    }
}