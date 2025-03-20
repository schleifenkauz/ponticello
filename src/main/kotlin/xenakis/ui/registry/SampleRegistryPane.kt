package xenakis.ui.registry

import bundles.PublicProperty
import bundles.publicProperty
import bundles.set
import fxutils.actions.ContextualizedAction
import fxutils.actions.collectActions
import fxutils.hasFiles
import fxutils.setupDropArea
import javafx.scene.image.Image
import javafx.scene.input.DataFormat
import javafx.scene.input.Dragboard
import org.kordamp.ikonli.evaicons.Evaicons
import org.kordamp.ikonli.material2.Material2MZ
import org.kordamp.ikonli.materialdesign2.MaterialDesignF
import reaktive.value.reactiveVariable
import xenakis.impl.asY
import xenakis.model.obj.SampleObject
import xenakis.model.project.samples
import xenakis.model.registry.SampleRegistry
import xenakis.sc.Identifier
import xenakis.ui.launcher.XenakisFiles
import xenakis.ui.launcher.XenakisLauncher.Companion.currentProject
import xenakis.ui.launcher.XenakisMainActivity
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

    override fun getActions(obj: SampleObject): List<ContextualizedAction> = actions.withContext(obj)

    override fun configureDragboard(obj: SampleObject, dragboard: Dragboard) {
        val scoreView = samples.context[XenakisMainActivity].scoreView
        val width = scoreView.getWidth(obj.duration)
        val height = scoreView.getPaneY(0.02.asY)
        dragboard.dragView = Image(obj.spectrogramFile.inputStream(), width, height, false, false)
    }

    override fun dataFormat(obj: SampleObject): DataFormat = SampleObject.DATA_FORMAT

    companion object : PublicProperty<SampleRegistryPane> by publicProperty("SampleRegistryPane") {
        private val actions = collectActions<SampleObject> {
            addAction("View sample") {
                icon(Evaicons.ACTIVITY)
                shortcut("Ctrl+Shift+O")
                executes {  obj -> Runtime.getRuntime().exec(arrayOf("xdg-open", obj.spectrogramFile.absolutePath))}
            }
            addAction("Reload") {
                icon(Material2MZ.SYNC)
                description("Reload from file system")
                shortcut("Shift+Alt+S")
                executes(SampleObject::sync)
            }
            addAction("Replace sample contents") {
                icon(MaterialDesignF.FILE_MUSIC_OUTLINE)
                executes { obj ->
                    val samples = obj.context[currentProject].samples
                    val newFile = samples.context[XenakisFiles].showOpenDialog("*.wav") ?: return@executes
                    if (samples.getSample(newFile) != null) return@executes
                    obj.loadFile(newFile)
                }
            }
        }
    }
}