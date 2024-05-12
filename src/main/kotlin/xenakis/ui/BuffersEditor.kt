package xenakis.ui

import hextant.serial.makeRoot
import javafx.css.PseudoClass
import javafx.scene.control.Label
import javafx.scene.input.DragEvent
import javafx.scene.input.TransferMode
import javafx.scene.layout.HBox
import javafx.scene.layout.VBox
import reaktive.Observer
import xenakis.impl.UDPSuperColliderClient
import xenakis.model.Buffers
import xenakis.model.XenakisProject
import xenakis.sc.*
import xenakis.sc.editor.IdentifierEditor
import xenakis.sc.view.IdentifierEditorControl
import java.io.File

class BuffersEditor(
    private val buffers: Buffers,
    private val project: XenakisProject,
    private val controller: XenakisController
) : VBox() {
    private val observers = mutableListOf<Observer>()

    init {
        styleClass("buffers")
        children.add(createHeader())
        for (buffer in buffers.buffers) displayBuffer(buffer)
        setupFileDropArea()
    }

    private fun setupFileDropArea() {
        setOnDragOver { ev ->
            if (accepts(ev)) {
                ev.acceptTransferModes(*TransferMode.COPY_OR_MOVE)
                ev.consume()
            }
        }
        setOnDragEntered { ev ->
            if (accepts(ev)) pseudoClassStateChanged(PseudoClass.getPseudoClass("drop-possible"), true)
            ev.consume()
        }
        setOnDragExited { ev ->
            pseudoClassStateChanged(PseudoClass.getPseudoClass("drop-possible"), false)
            ev.consume()
        }
        setOnDragDropped { ev ->
            val db = ev.dragboard
            if (db.hasFiles()) {
                for (file in db.files) {
                    val provisionalName = file.nameWithoutExtension
                    addBuffer(provisionalName, file)
                }
                ev.isDropCompleted = true
                ev.consume()
            }
        }
    }

    private fun accepts(ev: DragEvent) =
        ev.dragboard.hasFiles() && ev.dragboard.files.any { it.extension == "wav" }

    private fun createHeader(): HBox {
        val label = Label("Buffers").styleClass("buffers-heading")
        val space = infiniteSpace()
        val addBtn = Icon.Add.button(action = "Load new buffer") { addBuffer() }
        val reloadBtn = Icon.Repeat.button(action = "Reload SynthDefs") {
            val client = project.context[UDPSuperColliderClient]
            project.buffers.loadBuffers(client)
        }
        return HBox(label, space, addBtn, reloadBtn).styleClass("buffers-header")
    }

    private fun addBuffer() {
        val name = showTextInputDialog("Buffer name: ", project.context) { Identifier.isValid(it) } ?: return
        val file = controller.showOpenDialog("*.wav") ?: return
        addBuffer(name, file)
    }

    private fun addBuffer(name: String, file: File) {
        val buffer = FileBuffer(name, file)
        buffers.addBuffer(buffer, context = controller.client)
        displayBuffer(buffer)
    }

    private fun displayBuffer(buffer: Buffer) {
        val box = when (buffer) {
            is FileBuffer -> {
                val name = IdentifierEditor(project.context, buffer.name)
                name.makeRoot()
                val obs = name.result.observe { _, _, new -> buffers.renameBuffer(buffer, new.text, controller.client) }
                val nameControl = IdentifierEditorControl(name)
                observers.add(obs)
                val file = buffer.referencedFile.relativeTo(project.projectFile).toString()
                val fileLabel = Label(file)
                val space = infiniteSpace()
                val replace = Icon.Open.button(action = "Replace buffer contents") {
                    buffer.referencedFile = (controller.showOpenDialog("*.wav") ?: return@button)
                    buffers.reloadBuffer(buffer, context = controller.client)
                }
                val view = Icon.View.button(action = "View buffer contents") {
                    controller.client.postAsync("${buffer.variableName}.plot('${buffer.name}')")
                }
                val delete = Icon.Delete.button(action = "Remove this buffer") {
                    buffers.removeBuffer(buffer, context = controller.client)
                }
                HBox(nameControl, fileLabel, space, replace, view, delete)
            }
            is AllocatedBuffer -> TODO()
            NoBuffer -> throw AssertionError()
        }
        box.styleClass("buffer-box")
        children.add(box)
    }
}