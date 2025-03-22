package xenakis.ui.registry

import fxutils.actions.ContextualizedAction
import fxutils.actions.collectActions
import fxutils.prompt.compoundPrompt
import fxutils.setFixedWidth
import javafx.scene.Node
import javafx.scene.control.Label
import javafx.scene.control.Spinner
import javafx.scene.control.TextField
import org.kordamp.ikonli.evaicons.Evaicons
import org.kordamp.ikonli.material2.Material2MZ
import reaktive.value.fx.asProperty
import reaktive.value.now
import xenakis.impl.Logger
import xenakis.model.obj.BufferObject
import xenakis.model.registry.BufferRegistry
import xenakis.sc.client.SuperColliderClient

class BufferRegistryPane(
    buffers: BufferRegistry,
) : SuperColliderObjectRegistryPane<BufferObject>(buffers) {
    private val actions = collectActions<BufferObject> {
        addAction("View buffer contents") {
            icon(Evaicons.ACTIVITY)
            executes { buf ->
                val client = buf.context[SuperColliderClient]
                client.run("${buf.superColliderName}.plot('${buf.name.now}')")
            }
        }
        addAction("Sync with server") {
            icon(Material2MZ.SYNC)
            executes { buf -> sync(buf) }
        }
    }

    override fun addObject(name: String): BufferObject? {
        return compoundPrompt("Configure buffer $name") {
            val channelsSpinner = Spinner<Int>(1, 12, 2) named "Channels"
            val framesField = TextField() named "Frames"
            onConfirm {
                val channels = channelsSpinner.value
                val frames = framesField.text.toIntOrNull() ?: return@onConfirm null
                BufferObject.create(name, channels, frames)
            }
        }.showDialog(this)
    }

    override fun getContent(obj: BufferObject): List<Node> {
        val channelsSpinner = Spinner<Int>(1, 12, obj.channels.now).setFixedWidth(70.0)
        channelsSpinner.valueFactory.valueProperty().bindBidirectional(obj.channels.asProperty())
        val framesInput = TextField(obj.frames.now.toString())
        framesInput.promptText = "Number of frames"
        framesInput.id = "frames-input"
        val xLabel = Label("x")
        return listOf(channelsSpinner, xLabel, framesInput)
    }

    override fun getActions(obj: BufferObject): List<ContextualizedAction> = actions.withContext(obj)

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

    companion object
}