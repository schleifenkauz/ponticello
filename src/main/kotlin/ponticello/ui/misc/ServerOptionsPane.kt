package ponticello.ui.misc

import fxutils.controls.IntSpinner
import fxutils.prompt.CompoundPrompt
import fxutils.textField
import hextant.context.Context
import javafx.collections.FXCollections.observableList
import javafx.scene.control.ComboBox
import ponticello.model.ServerOptions
import ponticello.sc.client.SuperColliderClient

class ServerOptionsPane(
    private val context: Context,
    private val options: ServerOptions
) : CompoundPrompt<Unit>("Server options", labelWidth = 150.0, confirmText = "_Reboot") {
    private val device = textField(options.device) named "Device"
    private val numInputChannels = IntSpinner(0, 24, options.numInputChannels).minColumns(2) named "Input channels"
    private val numOutputChannels = IntSpinner(1, 24, options.numOutputChannels).minColumns(2) named "Output channels"
    private val memSize = IntSpinner(8192, 8192 * 64, options.memSize, 8192).minColumns(6) named "Runtime memory (kB)"
    private val sampleRate = ComboBox(observableList(listOf(44100, 48000, 96000, 192000))) named "Sample rate"
    private val numWireBufs = IntSpinner(8192, 8192 * 64, options.numWireBufs, step = 8192)
        .minColumns(6) named "Maximum Wire Bufs"

    init {
        sampleRate.value = options.sampleRate
        confirmButton.text = "Confirm and reboot server"
    }

    override fun confirm() {
        options.numInputChannels = numInputChannels.value()
        options.numOutputChannels = numOutputChannels.value()
        options.memSize = memSize.value()
        options.sampleRate = sampleRate.value
        options.numWireBufs = numWireBufs.value()
        options.device = device.text
        options.configureOptions(context[SuperColliderClient])
        context[SuperColliderClient].run("s.reboot")
    }
}