package xenakis.ui

import hextant.context.Context
import hextant.fx.registerShortcuts
import javafx.collections.FXCollections
import javafx.scene.control.ComboBox
import javafx.scene.control.Spinner
import javafx.scene.layout.HBox
import xenakis.model.ServerOptions

class ServerOptionsPane(
    private val context: Context,
    private val options: ServerOptions
) : DetailPane() {
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
        children.add(
            HBox(
                10.0,
                button("Cancel") { scene.window.hide() },
                button("Update and reboot server") { saveAndReboot() }
            )
        )
        registerShortcuts {
            on("Ctrl+S") { saveAndReboot() }
            on("ESCAPE") { scene.window.hide() }
        }
    }

    private fun saveAndReboot() {
        options.numInputChannels = numInputChannels.value
        options.numOutputChannels = numOutputChannels.value
        options.memSize = memSize.value
        options.sampleRate = sampleRate.value
        options.reboot(context)
        scene.window.hide()
    }

}