package xenakis.ui

import bundles.PublicProperty
import bundles.publicProperty
import bundles.set
import javafx.scene.image.Image
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
        samples.context[SampleRegistryPane] = this
    }

    override fun addObject() {
        addSample { file -> Identifier.truncate(file.nameWithoutExtension) }
    }

    private fun addSample(name: (File) -> String): SampleObject? {
        val file = controller.showOpenDialog("*.wav") ?: return null
        if (samples.getSample(file) != null) return null
        val sample = SampleObject(reactiveVariable(name(file)), file)
        samples.add(sample)
        return sample
    }

    public override fun addObject(name: String): SampleObject? = addSample { _ -> name }

    override fun ObjectBox<SampleObject>.configureObjectBox() {
        addAction(Icon.View, description = "View sample") {
            Runtime.getRuntime().exec(arrayOf("xdg-open", obj.spectrogramFile.absolutePath))
        }
        addAction(Icon.Repeat, "Reload sample and sync with server") { obj.sync() }
        addAction(Icon.Open, description = "Replace sample contents") {
            val newFile = controller.showOpenDialog("*.wav") ?: return@addAction
            if (samples.getSample(newFile) != null) return@addAction
            obj.loadFile(newFile)
        }
        addGrabber(SampleObject.DATA_FORMAT) {
            val width = samples.context[XenakisUI].scoreView.getWidth(obj.duration)
            val height = 150.0
            dragView = Image(obj.spectrogramFile.inputStream(), width, height, false, false)
        }
    }

    companion object : PublicProperty<SampleRegistryPane> by publicProperty("SampleRegistryPane")
}