package xenakis.ui

import hextant.fx.registerShortcuts
import javafx.collections.FXCollections
import javafx.scene.control.ComboBox
import javafx.scene.control.Label
import javafx.scene.control.Spinner
import javafx.scene.control.TextField
import javafx.scene.input.TransferMode
import javafx.scene.layout.HBox
import javafx.scene.layout.Region
import javafx.stage.StageStyle
import reaktive.value.binding.map
import reaktive.value.fx.asProperty
import reaktive.value.now
import reaktive.value.reactiveVariable
import xenakis.impl.SuperColliderClient
import xenakis.model.*
import xenakis.sc.Identifier

class BufferRegistryPane(
    private val buffers: BufferRegistry,
    private val project: XenakisProject,
    private val controller: XenakisController
) : ObjectRegistryPane<BufferObject>(buffers) {
    init {
        setupDropArea({ db -> db.hasFiles("wav") }, { ev ->
            for (file in ev.dragboard.files) {
                val buffer = FileBuffer.create(file)
                buffers.add(buffer)
            }
        })
        buffers.addView(this)
    }

    override fun reload() {
        val client = project.context[SuperColliderClient]
        buffers.run { client.initializeBuffers() }
    }

    override fun addObject() {
        val typeSelector = ComboBox(FXCollections.observableList(BufferObject.Type.values().asList()))
        typeSelector.value = BufferObject.Type.Allocate
        val nameInput = TextField() styleClass "prompt-text-field"
        nameInput.promptText = "Buffer name"
        val ok = Icon.Check.button(action = "Confirm")
        val layout = HBox(typeSelector, nameInput).centerChildrenVertically() styleClass "prompt"
        val window = SubWindow(layout, "Create new buffer", project.context, SubWindow.Type.Prompt)
        fun commit() {
            val type = typeSelector.value ?: return
            val name = nameInput.text
            if (!Identifier.isValid(name) || buffers.has(name)) return
            window.hide()
            addObject(type, name)
        }
        ok.setOnAction { commit() }
        layout.registerShortcuts {
            on("ENTER") { commit() }
        }
        window.sizeToScene()
        window.show()
    }

    override fun addObject(name: String) {
        val choices = BufferObject.Type.values().toList()
        showSelectorDialog("Buffer type", project.context, choices, null) { type ->
            addObject(type, name)
        }
    }

    private fun addObject(type: BufferObject.Type, name: String) {
        val buffer = when (type) {
            BufferObject.Type.File -> {
                val file = controller.showOpenDialog("*.wav") ?: return
                if (buffers.hasFile(file)) return
                FileBuffer.create(file, name)
            }

            BufferObject.Type.Allocate -> {
                val channelsSpinner = Spinner<Int>(1, 12, 2)
                val xLabel = Label("x")
                val framesField = TextField()
                framesField.promptText = "Number of frames"
                val layout = HBox(5.0, channelsSpinner, xLabel, framesField).centerChildrenVertically()
                layout.showDialog("Configure buffer", project.context, style = StageStyle.UTILITY) {
                    val channels = channelsSpinner.value
                    val frames = framesField.text.toIntOrNull() ?: return@showDialog null
                    AllocatedBuffer.create(name, channels, frames)
                } ?: return
            }

            BufferObject.Type.Reference -> ReferencedBuffer(reactiveVariable(name))
        }
        buffers.add(buffer)
    }

    override fun ObjectBox<BufferObject>.configureObjectBox() {
        when (obj) {
            is FileBuffer -> {
                val fileName = obj.referencedFile.map { f -> f.relativeTo(project.projectFile).toString() }
                val fileLabel = label(fileName)
                //TODO add file info as tooltip
                addExtraControl(fileLabel)
                addBufferInfo()
                addAction(Icon.Open, description = "Replace buffer contents") {
                    val newFile = controller.showOpenDialog("*.wav") ?: return@addAction
                    if (buffers.hasFile(newFile)) return@addAction
                    obj.loadFile(newFile)
                    buffers.reloadBuffer(obj, context = controller.client)
                }
            }

            is AllocatedBuffer -> {
                val channelsSpinner = Spinner<Int>(1, 12, obj.channels.now)
                channelsSpinner.prefWidth = 70.0
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
        addAction(Icon.View, description = "View buffer contents") {
            controller.client.run("${obj.variableName}.plot('${obj.name.now}')")
        }
        addAction(Icon.Repeat, "Sync with server") {
            if (obj is AllocatedBuffer) {
                val framesInput = lookup("#frames-input") as TextField
                val n = framesInput.text.toIntOrNull()
                if (n == null) {
                    alertError("Not a valid number of frames specified.")
                    return@addAction
                }
                obj.frames.set(n)
            }
            obj.sync(buffers.context[SuperColliderClient])
        }
        setOnDragDetected { ev ->
            val db = startDragAndDrop(TransferMode.COPY)
            db.setContent(mapOf(BufferObject.DATA_FORMAT to obj.name.now))
            ev.consume()
        }
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