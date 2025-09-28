package ponticello.ui.record

import fxutils.prompt.CompoundPrompt
import fxutils.styleClass
import javafx.scene.control.TextField
import ponticello.model.obj.withName
import ponticello.model.record.CaptureSource
import ponticello.model.record.LiveBufferObject
import ponticello.sc.Identifier
import reaktive.value.reactiveVariable
import javax.sound.sampled.AudioFormat

class NewLiveBufferPrompt(format: AudioFormat) : CompoundPrompt<LiveBufferObject>("Add LiveBuffer") {
    private val nameField = TextField() styleClass "sleek-text-field" named "Name:"
    private var source: CaptureSource = CaptureSource.None
    private val sourceSelector = AudioSourceSelectorPrompt(format).selectorButton(::source)

    init {
        addItem("Source: ", sourceSelector)
        content.prefWidth = 300.0
    }

    override fun confirm(): LiveBufferObject? {
        val name = nameField.text.takeIf { Identifier.isValid(it) } ?: return null
        if (source is CaptureSource.None) return null
        val viewConfig = LiveBufferViewConfig.Waveform.default()
        return LiveBufferObject(source, viewConfig, _enabled = reactiveVariable(true)).withName(name)
    }
}