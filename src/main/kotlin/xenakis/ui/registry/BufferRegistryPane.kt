package xenakis.ui.registry

import javafx.scene.control.Label
import javafx.scene.control.Spinner
import javafx.scene.control.TextField
import reaktive.value.fx.asProperty
import reaktive.value.now
import xenakis.model.Logger
import xenakis.model.obj.BufferObject
import xenakis.model.registry.BufferRegistry
import xenakis.ui.Icon
import xenakis.ui.XenakisController
import xenakis.ui.impl.setFixedWidth
import xenakis.ui.prompt.compoundInput

class BufferRegistryPane(
    buffers: BufferRegistry,
    private val controller: XenakisController
) : SuperColliderObjectRegistryPane<BufferObject>(buffers) {
    init {
        buffers.addListener(this)
    }

    override fun addObject(name: String): BufferObject? {
        return compoundInput("Configure buffer $name") {
            val channelsSpinner = Spinner<Int>(1, 12, 2) named "Channels"
            val framesField = TextField() named "Frames"
            onConfirm {
                val channels = channelsSpinner.value
                val frames = framesField.text.toIntOrNull() ?: return@onConfirm null
                BufferObject.create(name, channels, frames)
            }
        }.showDialog(registry.context, this)
    }

    override fun ObjectBox<BufferObject>.configureObjectBox() {
        addAction(Icon.View, description = "View buffer contents") {
            controller.client.run("${obj.superColliderName}.plot('${obj.name.now}')")
        }
        addAction(Icon.Repeat, "Sync with server") { sync(obj) }
        val channelsSpinner = Spinner<Int>(1, 12, obj.channels.now).setFixedWidth(70.0)
        channelsSpinner.valueFactory.valueProperty().bindBidirectional(obj.channels.asProperty())
        val framesInput = TextField(obj.frames.now.toString())
        framesInput.promptText = "Number of frames"
        framesInput.id = "frames-input"
        val xLabel = Label("x")
        addExtraControl(channelsSpinner, xLabel, framesInput)
    }

    private fun sync(obj: BufferObject) {
        val framesInput = lookup("#frames-input") as TextField
        val n = framesInput.text.toIntOrNull()
        if (n == null) {
            Logger.error("Not a valid number of frames specified.", Logger.Category.Buffers)
            return
        }
        obj.frames.set(n)
        obj.sync()
    }
}