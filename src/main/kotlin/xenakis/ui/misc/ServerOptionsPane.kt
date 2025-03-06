package xenakis.ui.misc

import fxutils.prompt.CompoundPrompt
import fxutils.textField
import hextant.context.Context
import javafx.collections.FXCollections.observableList
import javafx.scene.control.ComboBox
import javafx.scene.control.Spinner
import xenakis.model.ServerOptions

class ServerOptionsPane(
    private val context: Context,
    private val options: ServerOptions
) : CompoundPrompt<Unit>("Server options") {
    private val device = textField(options.device) named "Device"
    private val numInputChannels = Spinner<Int>(0, 24, options.numInputChannels) named "Input channels"
    private val numOutputChannels = Spinner<Int>(1, 24, options.numOutputChannels) named "Output channels"
    private val memSize = Spinner<Int>(8192, 8192 * 64, options.memSize, 8192) named "Runtime memory (kB)"
    private val sampleRate = ComboBox(observableList(listOf(44100, 48000, 96000, 192000))) named "Sample rate"
    private val numWireBufs = Spinner<Int>(8192, 8192 * 64, options.numWireBufs) named "Maximum Wire Bufs"

    init {
        sampleRate.value = options.sampleRate
        confirmButton.text = "Confirm and reboot server"
    }

    override fun confirm() {
        options.numInputChannels = numInputChannels.value
        options.numOutputChannels = numOutputChannels.value
        options.memSize = memSize.value
        options.sampleRate = sampleRate.value
        options.numWireBufs = numWireBufs.value
        options.device = device.text
        options.reboot(context)
    }
}