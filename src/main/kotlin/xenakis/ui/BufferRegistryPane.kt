package xenakis.ui

import javafx.scene.control.Label
import javafx.scene.control.Spinner
import reaktive.value.now
import reaktive.value.reactiveVariable
import xenakis.impl.SuperColliderClient
import xenakis.model.*

class BufferRegistryPane(
    private val buffers: BufferRegistry,
    private val project: XenakisProject,
    private val controller: XenakisController
) : ObjectRegistryPane<BufferObject>(buffers) {
    init {
        setupFileDropArea(exactlyOne = false, "wav") { file, _ ->
            val defaultName = file.nameWithoutExtension
            showNamePrompt(buffers, defaultName) { name ->
                val buffer = FileBuffer(reactiveVariable(name), file)
                buffers.add(buffer)
            }
        }
        buffers.addView(this)
    }

    override fun reload() {
        val client = project.context[SuperColliderClient]
        buffers.run { client.reloadBuffers() }
    }

    override fun addObject(name: String) {
        val file = controller.showOpenDialog("*.wav") ?: return
        val buffer = FileBuffer(reactiveVariable(name), file)
        buffers.add(buffer)
    }

    override fun ObjectBox<BufferObject>.configureObjectBox() {
        when (obj) {
            is FileBuffer -> {
                val file = obj.referencedFile.relativeTo(project.projectFile).toString()
                val fileLabel = Label(file)
                addExtraControl(fileLabel)
                addAction(Icon.Open, description = "Replace buffer contents") {
                    obj.referencedFile = controller.showOpenDialog("*.wav") ?: return@addAction
                    fileLabel.text = obj.referencedFile.relativeTo(project.projectFile).toString()
                    buffers.reloadBuffer(obj, context = controller.client)
                }
                addAction(Icon.View, description = "View buffer contents") {
                    controller.client.run("${obj.variableName}.plot('${obj.name.now}')")
                }
            }

            is AllocatedBuffer -> {
                val channelsSpinner = Spinner<Int>(1, 10, 2)
                TODO()
            }

            NoBuffer -> throw AssertionError()
        }
    }
}