package xenakis.ui

import hextant.fx.add
import javafx.scene.control.Label
import javafx.scene.control.Spinner
import javafx.scene.layout.HBox
import javafx.scene.layout.VBox
import reaktive.value.now
import reaktive.value.reactiveVariable
import xenakis.impl.SuperColliderClient
import xenakis.model.Buffers
import xenakis.model.XenakisProject
import xenakis.sc.*
import java.io.File

class BuffersPane(
    private val buffers: Buffers,
    private val project: XenakisProject,
    private val controller: XenakisController
) : VBox() {
    init {
        styleClass("tool-pane")
        children.add(createHeader())
        for (buffer in buffers.buffers) displayBuffer(buffer)
        setupFileDropArea(exactlyOne = false, "wav") { file, _ ->
            val defaultName = file.nameWithoutExtension
            showTextPrompt("Buffer name", defaultName, project.context) { name ->
                if (Identifier.isValid(name)) {
                    addBuffer(name, file)
                    true
                } else false
            }
        }
    }

    private fun createHeader(): HBox {
        val label = Label("Buffers").styleClass("tool-pane-heading")
        val space = infiniteSpace()
        val addBtn = Icon.Add.button(action = "Load new buffer") { addBuffer() }
        val reloadBtn = Icon.Repeat.button(action = "Reload SynthDefs") {
            val client = project.context[SuperColliderClient]
            project.buffers.run { client.loadBuffers() }
        }
        return HBox(label, space, addBtn, reloadBtn).styleClass("tool-pane-header")
    }

    private fun addBuffer() {
        showTextPrompt("Buffer name: ", "", project.context) { name ->
            if (Identifier.isValid(name)) {
                val file = controller.showOpenDialog("*.wav") ?: return@showTextPrompt false
                addBuffer(name, file)
                true
            } else false
        }
    }

    private fun addBuffer(name: String, file: File) {
        val buffer = FileBuffer(reactiveVariable(name), file)
        buffers.addBuffer(buffer, context = controller.client)
        displayBuffer(buffer)
    }

    private fun displayBuffer(buffer: Buffer) {
        val nameControl = NameControl(buffer)
        val box = HBox(nameControl)
        when (buffer) {
            is FileBuffer -> {
                val file = buffer.referencedFile.relativeTo(project.projectFile).toString()
                val fileLabel = Label(file)
                val space = infiniteSpace()
                val replace = Icon.Open.button(action = "Replace buffer contents") {
                    buffer.referencedFile = controller.showOpenDialog("*.wav") ?: return@button
                    fileLabel.text = buffer.referencedFile.relativeTo(project.projectFile).toString()
                    buffers.reloadBuffer(buffer, context = controller.client)
                }
                box.children.addAll(fileLabel, space, replace)
            }

            is AllocatedBuffer -> {
                val channelsSpinner = Spinner<Int>(1, 10, 2)
                TODO()
            }

            NoBuffer -> throw AssertionError()
        }
        box.add(Icon.View.button(action = "View buffer contents") {
            controller.client.run("${buffer.variableName}.plot('${buffer.name.now}')")
        })
        box.add(Icon.Delete.button(action = "Remove this buffer") {
            buffers.removeBuffer(buffer, context = controller.client)
        })
        box.styleClass("buffer-box")
        children.add(box)
    }
}