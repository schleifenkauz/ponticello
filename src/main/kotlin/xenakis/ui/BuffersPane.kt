package xenakis.ui

import hextant.fx.add
import hextant.serial.makeRoot
import javafx.scene.control.Button
import javafx.scene.control.Label
import javafx.scene.control.Spinner
import javafx.scene.layout.HBox
import javafx.scene.layout.VBox
import reaktive.Observer
import reaktive.event.observe
import xenakis.impl.UDPSuperColliderClient
import xenakis.model.Buffers
import xenakis.model.XenakisProject
import xenakis.sc.*
import xenakis.sc.editor.IdentifierEditor
import xenakis.sc.view.IdentifierEditorControl
import java.io.File

class BuffersPane(
    private val buffers: Buffers,
    private val project: XenakisProject,
    private val controller: XenakisController
) : VBox() {
    private val observers = mutableListOf<Observer>()

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
            val client = project.context[UDPSuperColliderClient]
            project.buffers.loadBuffers(client)
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
        val buffer = FileBuffer(Identifier(name), file)
        buffers.addBuffer(buffer, context = controller.client)
        displayBuffer(buffer)
    }

    private fun displayBuffer(buffer: Buffer) {
        val name = IdentifierEditor(project.context, buffer.name.text)
        name.makeRoot()
        val obs = name.result.observe { _, _, new -> buffers.renameBuffer(buffer, new.text, controller.client) }
        val nameControl = IdentifierEditorControl(name)
        nameControl.userData = nameControl.onChangeCommited.observe { oldName: String, newName: String ->
            showYesNoDialog("Rename references", default = true)
            project.renamedSynthDef(oldName, newName)
        }

        observers.add(obs)
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
            controller.client.postAsync("${buffer.variableName}.plot('${buffer.name.text}')")
        })
        box.add(Icon.Delete.button(action = "Remove this buffer") {
            buffers.removeBuffer(buffer, context = controller.client)
        })
        box.styleClass("buffer-box")
        children.add(box)
    }

    private fun select(selector: Button, text: String) {

    }
}