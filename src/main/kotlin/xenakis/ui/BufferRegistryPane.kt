package xenakis.ui

import javafx.scene.control.Label
import javafx.scene.control.Spinner
import javafx.scene.control.TextField
import javafx.scene.input.TransferMode
import javafx.scene.layout.Region
import reaktive.value.binding.map
import reaktive.value.fx.asProperty
import reaktive.value.now
import reaktive.value.reactiveVariable
import xenakis.model.*
import xenakis.ui.prompt.compoundInput
import xenakis.ui.prompt.showSelectorDialog

class BufferRegistryPane(
    private val buffers: BufferRegistry,
    private val project: XenakisProject,
    private val controller: XenakisController
) : SuperColliderObjectRegistryPane<BufferObject>(buffers) {
    init {
        setupDropArea({ db -> db.hasFiles("wav") }, { ev ->
            for (file in ev.dragboard.files) {
                val buffer = FileBuffer.create(file)
                buffers.add(buffer)
            }
        })
        buffers.addListener(this)
    }

    override fun addObject() {
        val options = BufferObject.Type.values().asList()
        val default = BufferObject.Type.Allocate
        showCreateNewDialog(options, default, ::addObject)
    }

    override fun addObject(name: String): BufferObject? {
        val choices = BufferObject.Type.values().toList()
        val type = showSelectorDialog(project.context, "Buffer type", choices, anchorNode = this) ?: return null
        return addObject(type, name)
    }

    private fun addObject(type: BufferObject.Type, name: String): BufferObject? {
        return when (type) {
            BufferObject.Type.File -> {
                val file = controller.showOpenDialog("*.wav") ?: return null
                if (buffers.getBufferFor(file) != null) return null
                FileBuffer.create(file, name)
            }

            BufferObject.Type.Allocate -> compoundInput<AllocatedBuffer>("Configure buffer $name") {
                val channelsSpinner = Spinner<Int>(1, 12, 2) named "Channels"
                val framesField = TextField() named "Frames"
                onConfirm {
                    val channels = channelsSpinner.value
                    val frames = framesField.text.toIntOrNull() ?: return@onConfirm null
                    AllocatedBuffer.create(name, channels, frames)
                }
            }.showDialog(registry.context)

            BufferObject.Type.Reference -> ReferencedBuffer(reactiveVariable(name))
        }
    }

    override fun ObjectBox<BufferObject>.configureObjectBox() {
        addAction(Icon.View, description = "View buffer contents") {
            controller.client.run("${obj.superColliderName}.plot('${obj.name.now}')")
        }
        addAction(Icon.Repeat, "Sync with server") { sync(obj) }
        when (obj) {
            is FileBuffer -> {
                val fileName = obj.referencedFile.map { f -> f.relativeTo(project.projectDirectory).toString() }
                val fileLabel = label(fileName)
                //TODO add file info as tooltip
                addExtraControl(fileLabel)
                addBufferInfo()
                addAction(Icon.Open, description = "Replace buffer contents") {
                    val newFile = controller.showOpenDialog("*.wav") ?: return@addAction
                    if (buffers.getBufferFor(newFile) != null) return@addAction
                    obj.loadFile(newFile)
                    obj.sync()
                }
            }

            is AllocatedBuffer -> {
                val channelsSpinner = Spinner<Int>(1, 12, obj.channels.now).setFixedWidth(70.0)
                channelsSpinner.valueFactory.valueProperty().bindBidirectional(obj.channels.asProperty())
                val framesInput = TextField(obj.frames.now.toString())
                framesInput.promptText = "Number of frames"
                framesInput.id = "frames-input"
                val xLabel = Label("x")
                addExtraControl(channelsSpinner, xLabel, framesInput)
            }

            is ReferencedBuffer -> {
                addBufferInfo()
            }
        }
        setOnDragDetected { ev ->
            val db = startDragAndDrop(TransferMode.COPY)
            db.setContent(mapOf(BufferObject.DATA_FORMAT to obj.name.now))
            ev.consume()
        }
    }

    private fun sync(obj: BufferObject) {
        if (obj is AllocatedBuffer) {
            val framesInput = lookup("#frames-input") as TextField
            val n = framesInput.text.toIntOrNull()
            if (n == null) {
                alertError("Not a valid number of frames specified.")
                return
            }
            obj.frames.set(n)
        }
        obj.sync()
    }

    private fun ObjectBox<BufferObject>.addBufferInfo() {
        val channelsLabel = label(obj.channels.map { n -> if (n == 0) "?" else n.toString() })
        val xLabel = Label("x")
        val framesLabel = label(obj.frames.map { n -> if (n == 0) "?" else n.toString() })
        val space = Region()
        space.prefWidth = 15.0
        addExtraControl(space, channelsLabel, xLabel, framesLabel)
    }
}