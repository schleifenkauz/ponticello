package xenakis.ui.registry

import fxutils.actions.ContextualizedAction
import fxutils.actions.collectActions
import fxutils.prompt.compoundPrompt
import fxutils.registerShortcuts
import fxutils.setFixedWidth
import hextant.fx.HextantTextField
import javafx.event.Event
import javafx.scene.Node
import javafx.scene.control.Label
import javafx.scene.control.Spinner
import javafx.scene.control.TextField
import javafx.scene.input.DataFormat
import org.kordamp.ikonli.evaicons.Evaicons
import org.kordamp.ikonli.material2.Material2MZ
import reaktive.value.fx.asProperty
import reaktive.value.now
import xenakis.impl.Logger
import xenakis.impl.parseDecimal
import xenakis.model.obj.AllocatedBufferObject
import xenakis.model.obj.BufferObject
import xenakis.model.registry.BufferRegistry
import xenakis.sc.client.SuperColliderClient

class AllocatedBufferRegistryPane(buffers: BufferRegistry) : ObjectRegistryPane<BufferObject>(buffers) {
    init {
        setup()
    }

    override fun createNewObject(name: String, ev: Event?): BufferObject? {
        return compoundPrompt("Configure buffer $name") {
            val channelsSpinner = Spinner<Int>(1, 12, 2) named "Channels"
            val durationField = TextField() named "Duration (s)"
            onConfirm {
                val channels = channelsSpinner.value
                val duration = durationField.text.parseDecimal() ?: return@onConfirm null
                AllocatedBufferObject.create(name, channels, duration)
            }
        }.showDialog(this)
    }

    override fun filter(obj: BufferObject): Boolean = obj is AllocatedBufferObject

    override fun getItemContent(obj: BufferObject): List<Node> {
        obj as AllocatedBufferObject
        val channelsSpinner = Spinner<Int>(1, 12, obj.channels.now).setFixedWidth(70.0)
        channelsSpinner.valueFactory.valueProperty().bindBidirectional(obj.channels.asProperty())
        val durationInput = HextantTextField(obj.duration().now.toString())
        durationInput.setOnAction { syncBuffer(obj, durationInput) }
        durationInput.focusedProperty().addListener { _, _, focused ->
            if (!focused) {
                resetDurationInput(durationInput, obj)
            }
        }
        durationInput.registerShortcuts {
            on("ESCAPE") {
                resetDurationInput(durationInput, obj)
            }
        }
        durationInput.promptText = "Number of frames"
        durationInput.id = "duration-input"
        val xLabel = Label("x")
        return listOf(channelsSpinner, xLabel, durationInput)
    }

    private fun resetDurationInput(durationInput: HextantTextField, obj: BufferObject) {
        durationInput.text = obj.duration().now.toString()
    }

    override fun getActions(box: ObjectBox<BufferObject>): List<ContextualizedAction> = actions.withContext(box)

    override fun dataFormat(obj: BufferObject): DataFormat = BufferObject.DATA_FORMAT

    companion object {
        private val actions = collectActions<ObjectBox<BufferObject>> {
            addAction("View buffer contents") {
                icon(Evaicons.ACTIVITY)
                executes { box ->
                    val buf = box.obj
                    val client = buf.context[SuperColliderClient]
                    client.run("${buf.superColliderName}.plot('${buf.name.now}')")
                }
            }
            addAction("Reallocate") {
                icon(Material2MZ.SYNC)
                shortcut("Ctrl+U")
                executes { box -> sync(box) }
            }
        }

        private fun sync(box: ObjectBox<BufferObject>) {
            val durationInput = box.lookup("#duration-input") as TextField
            syncBuffer(box.obj as AllocatedBufferObject, durationInput)
        }

        private fun syncBuffer(buffer: AllocatedBufferObject, durationInput: TextField) {
            val n = durationInput.text.parseDecimal()
            if (n == null) {
                Logger.warn("Not a valid number of frames specified.", Logger.Category.Buffers)
                return
            }
            buffer.duration.set(n)
            buffer.sync()
        }
    }
}