package ponticello.ui.record

import fxutils.prompt.CompoundPrompt
import fxutils.styleClass
import javafx.scene.control.TextField
import ponticello.model.obj.withName
import ponticello.model.record.CaptureSource
import ponticello.model.record.LiveBufferObject
import ponticello.sc.Identifier
import reaktive.value.ReactiveVariable
import reaktive.value.binding.equalTo
import reaktive.value.binding.map
import reaktive.value.binding.or
import reaktive.value.fx.asObservableValue
import reaktive.value.fx.asReactiveValue
import reaktive.value.now
import reaktive.value.reactiveVariable
import javax.sound.sampled.AudioFormat

class NewLiveBufferPrompt(format: AudioFormat) : CompoundPrompt<LiveBufferObject>("Add LiveBuffer") {
    private val nameField = TextField() styleClass "sleek-text-field" named "Name:"
    private val source: ReactiveVariable<CaptureSource> = reactiveVariable(CaptureSource.None)
    private val sourceSelector = AudioSourceSelectorPrompt(format).selectorButton(source)

    init {
        addItem("Source: ", sourceSelector)
        content.prefWidth = 300.0
        val nameInvalid = nameField.textProperty().asReactiveValue().map { name -> !Identifier.isValid(name) }
        val noSource = source.equalTo(CaptureSource.None)
        confirmButton.disableProperty().bind(nameInvalid.or(noSource).asObservableValue())
    }

    override fun confirm(): LiveBufferObject? {
        val name = nameField.text.takeIf { Identifier.isValid(it) } ?: return null
        if (source.now is CaptureSource.None) return null
        val viewConfig = LiveBufferViewConfig.Waveform.default()
        return LiveBufferObject(source.now, viewConfig, _enabled = reactiveVariable(true)).withName(name)
    }
}