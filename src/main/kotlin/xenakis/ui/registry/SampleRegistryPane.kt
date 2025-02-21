package xenakis.ui.registry

import bundles.PublicProperty
import bundles.publicProperty
import bundles.set
import javafx.scene.image.Image
import javafx.scene.input.TransferMode
import reaktive.value.reactiveVariable
import xenakis.impl.asY
import xenakis.model.obj.SampleObject
import xenakis.model.registry.SampleRegistry
import xenakis.sc.Identifier
import xenakis.ui.Icon
import xenakis.ui.XenakisMainScreen
import xenakis.ui.impl.hasFiles
import xenakis.ui.impl.setupDropArea
import xenakis.ui.launcher.XenakisFiles
import xenakis.ui.launcher.XenakisLauncher.Companion.currentProject
import java.io.File

class SampleRegistryPane(
    private val samples: SampleRegistry
) : SuperColliderObjectRegistryPane<SampleObject>(samples) {
    init {
        setupDropArea({ db -> db.hasFiles("wav") }, { ev ->
            for (file in ev.dragboard.files) {
                samples.getOrAdd(file)
            }
        })
        samples.addListener(this)
        samples.context[SampleRegistryPane] = this
    }

    override fun addObject() {
        addSample { file -> Identifier.truncate(file.nameWithoutExtension) }
    }

    private fun addSample(name: (File) -> String): SampleObject? {
        val file = samples.context[XenakisFiles].showOpenDialog("*.wav") ?: return null
        if (samples.getSample(file) != null) return null
        val sample = SampleObject.create(samples.context[currentProject], reactiveVariable(name(file)), file)
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
            val newFile = samples.context[XenakisFiles].showOpenDialog("*.wav") ?: return@addAction
            if (samples.getSample(newFile) != null) return@addAction
            obj.loadFile(newFile)
        }
        addGrabber(SampleObject.DATA_FORMAT, TransferMode.COPY) {
            val scoreView = samples.context[XenakisMainScreen].scoreView
            val width = scoreView.getWidth(obj.duration)
            val height = scoreView.getPaneY(0.02.asY)
            dragView = Image(obj.spectrogramFile.inputStream(), width, height, false, false)
        }
    }

    companion object : PublicProperty<SampleRegistryPane> by publicProperty("SampleRegistryPane")
}