package ponticello.ui.record

import fxutils.bindPseudoClassState
import fxutils.controls.IntSpinner
import fxutils.prompt.CompoundPrompt
import fxutils.removeColumn
import fxutils.removeRow
import fxutils.styleClass
import hextant.context.Context
import javafx.scene.control.Button
import javafx.scene.control.TextField
import javafx.scene.layout.GridPane
import ponticello.model.obj.withName
import ponticello.model.record.CaptureSource
import ponticello.model.record.ChannelConfiguration
import ponticello.model.record.LiveBufferObject
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

class NewLiveBufferPrompt(context: Context) : CompoundPrompt<LiveBufferObject>("Add LiveBuffer") {
    private val nameField = TextField() styleClass "sleek-text-field"
    private val source: ReactiveVariable<CaptureSource> = reactiveVariable(CaptureSource.None)
    private val channels = reactiveVariable(0)
    private val channelsSpinner = IntSpinner(channels, 0..0)
    private val sourceSelector = AudioSourceSelectorPrompt(context).selectorButton(source)

    private val mapping = mutableListOf<ReactiveVariable<Int>>()
    private val mappingGrid = GridPane()

    private val sourceObserver: Observer
    private val channelsObserver: Observer

    init {
        content.prefWidth = 300.0

        content.spacing = 5.0
        addItem("Source: ", sourceSelector)
        addItem("Channels: ", channelsSpinner)
        channelsSpinner.isDisable = true
        mappingGrid.hgap = 5.0
        mappingGrid.vgap = 5.0
        content.children.add(mappingGrid)
        addItem("Name: ", nameField)

        sourceObserver = source.observe { _, old, new ->
            when (new) {
                is CaptureSource.Mixer -> {
                    channelsSpinner.isDisable = false
                    if (channels.now == 0) {
                        channels.set(new.channels)
                    }
                    channelsSpinner.setRange(1..new.channels)
                    mappingGrid.isManaged = true
                    nameField.text = Identifier.truncate(new.name)
                }

                CaptureSource.None -> {
                    channelsSpinner.isDisable = true
                    mappingGrid.isManaged = false
                }
            }
            if (new.channels > old.channels) {
                for (ch in 0 until channels.now) {
                    for (i in old.channels until new.channels) {
                        mappingGrid.add(createCell(i, mapping[ch]), /*column*/i, /*row*/ ch)
                    }
                }
                window.sizeToScene()
            } else if (new.channels < old.channels) {
                for (i in new.channels until old.channels) {
                    mappingGrid.removeColumn(i)
                }
                window.sizeToScene()
            }
        }

        channelsObserver = channels.observe { _, prevChannels, channels ->
            if (channels > prevChannels) {
                for (ch in prevChannels until channels) {
                    if (ch !in mapping.indices) {
                        val inputIndex = reactiveVariable(ch)
                        mapping.add(inputIndex)
                    }
                    for (i in 0 until source.now.channels) {
                        mappingGrid.add(createCell(i, mapping[ch]), /*column*/i,/*row*/ ch)
                    }
                }
                window.sizeToScene()
            } else if (channels < prevChannels) {
                for (ch in channels until prevChannels) {
                    mappingGrid.removeRow(ch)
                }
                window.sizeToScene()
            }
        }

        val nameInvalid = nameField.textProperty().asReactiveValue().map { name -> !Identifier.isValid(name) }
        val noSource = source.equalTo(CaptureSource.None)
        confirmButton.disableProperty().bind(nameInvalid.or(noSource).asObservableValue())
    }

    private fun createCell(targetIndex: Int, mappingVar: ReactiveVariable<Int>): Button {
        val btn = Button(targetIndex.toString()) styleClass "channel-grid-cell"
        btn.setOnAction { mappingVar.set(targetIndex) }
        btn.userData = btn.bindPseudoClassState("selected", mappingVar.equalTo(targetIndex))
        return btn
    }

    override fun confirm(): LiveBufferObject? {
        val name = nameField.text.takeIf { Identifier.isValid(it) } ?: return null
        if (source.now is CaptureSource.None) return null
        val viewConfig = LiveBufferViewConfig.Waveform.default()
        val channelConfig = ChannelConfiguration(
            source.now.channels, channels.now,
            mapping.take(channels.now).map { inputIndex -> inputIndex.now }
        )
        val enabled = reactiveVariable(true)
        return LiveBufferObject(source.now, channelConfig, viewConfig, enabled).withName(name)
    }
}