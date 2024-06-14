package xenakis.ui

import javafx.scene.input.TransferMode
import reaktive.value.now
import reaktive.value.reactiveVariable
import xenakis.model.SampleObject
import xenakis.model.SampleRegistry
import xenakis.sc.Identifier
import java.io.File

class SampleRegistryPane(
    private val samples: SampleRegistry, private val controller: XenakisController
) : SuperColliderObjectRegistryPane<SampleObject>(samples) {
    init {
        setupDropArea({ db -> db.hasFiles("wav") }, { ev ->
            for (file in ev.dragboard.files) {
                if (samples.getSample(file) != null) return@setupDropArea
                val name = reactiveVariable(Identifier.truncate(file.nameWithoutExtension))
                val buffer = SampleObject(name, file)
                samples.add(buffer)
            }
        })
        samples.addView(this)
    }

    override fun addObject(name: (File) -> String) {
        val file = controller.showOpenDialog("*.wav") ?: return
        if (samples.getSample(file) != null) return
        val sample = SampleObject(reactiveVariable(name(file)), file)
        samples.add(sample)
    }

    override fun addObject(name: String) {
        addObject { _ -> name }
    }

    override fun ObjectBox<SampleObject>.configureObjectBox() {
        addAction(Icon.View, description = "View sample") {
            Runtime.getRuntime().exec(arrayOf("xdg-open", obj.spectrogramFile.absolutePath))
        }
        addAction(Icon.Repeat, "Reload sample and sync with server") { obj.sync() }
        addAction(Icon.Open, description = "Replace sample contents") {
            val newFile = controller.showOpenDialog("*.wav") ?: return@addAction
            if (samples.getSample(newFile) != null) return@addAction
            obj.loadFile(newFile)
            obj.sync()
        }
        setOnDragDetected { ev ->
            val db = startDragAndDrop(TransferMode.COPY)
            db.setContent(mapOf(SampleObject.DATA_FORMAT to obj.name.now))
            ev.consume()
        }
    }
}