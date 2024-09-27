package xenakis.ui

import hextant.context.Context
import javafx.collections.FXCollections
import javafx.scene.control.ComboBox
import javafx.scene.control.Spinner
import xenakis.model.ServerOptions
import xenakis.ui.prompt.CompoundPrompt

class ServerOptionsPane(
    private val context: Context,
    private val options: ServerOptions
) : CompoundPrompt<Unit>("Server options") {
    private val numInputChannels = Spinner<Int>(0, 24, options.numInputChannels)
    private val numOutputChannels = Spinner<Int>(1, 24, options.numOutputChannels)
    private val memSize = Spinner<Int>(8192, 8192 * 64, options.memSize, 8192)
    private val sampleRate = ComboBox(FXCollections.observableList(listOf(44100, 48000, 96000, 192000)))

    init {
        sampleRate.value = options.sampleRate
        addItem("Input channels", numInputChannels)
        addItem("Output channels", numOutputChannels)
        addItem("Runtime memory (kB)", memSize)
        addItem("Sample rate", sampleRate)
        confirmButton.text = "Confirm and reboot server"
    }

    override fun confirm() {
        options.numInputChannels = numInputChannels.value
        options.numOutputChannels = numOutputChannels.value
        options.memSize = memSize.value
        options.sampleRate = sampleRate.value
        options.reboot(context)
    }
}