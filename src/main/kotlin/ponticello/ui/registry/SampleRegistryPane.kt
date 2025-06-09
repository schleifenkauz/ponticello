package ponticello.ui.registry

import bundles.PublicProperty
import bundles.publicProperty
import bundles.set
import fxutils.actions.ContextualizedAction
import fxutils.actions.collectActions
import fxutils.hasFiles
import fxutils.prompt.YesNoPrompt
import fxutils.setupDropArea
import javafx.event.Event
import javafx.scene.input.DataFormat
import org.kordamp.ikonli.evaicons.Evaicons
import org.kordamp.ikonli.material2.Material2MZ
import org.kordamp.ikonli.materialdesign2.MaterialDesignC
import org.kordamp.ikonli.materialdesign2.MaterialDesignF
import org.kordamp.ikonli.materialdesign2.MaterialDesignP
import ponticello.model.obj.BufferObject
import ponticello.model.obj.SampleObject
import ponticello.model.obj.project
import ponticello.model.project.buffers
import ponticello.model.registry.BufferRegistry
import ponticello.sc.Identifier
import ponticello.ui.actions.undoable
import ponticello.ui.launcher.PonticelloFiles
import ponticello.ui.score.ScoreObjectDuplicator
import java.io.File

class SampleRegistryPane(
    private val samples: BufferRegistry,
) : ObjectRegistryPane<BufferObject>(samples) {
    init {
        setupDropArea({ db -> db.hasFiles("wav") }, { ev ->
            for (file in ev.dragboard.files) {
                samples.getOrAdd(file)
            }
        })
        samples.context[SampleRegistryPane] = this
        setup()
    }

    override fun addObject(ev: Event?) {
        val obj = loadNewSample { file -> Identifier.truncate(file.nameWithoutExtension) } ?: return
        registry.add(obj)
    }

    private fun loadNewSample(name: (File) -> String): SampleObject? {
        val file = samples.context[PonticelloFiles].showOpenDialog("*.wav") ?: return null
        if (samples.getSample(file) != null) return null
        val sample = SampleObject.create(samples.context.project, name(file), file)
        return sample
    }

    public override fun createNewObject(name: String, ev: Event?): SampleObject? = loadNewSample { _ -> name }

    override fun filter(obj: BufferObject): Boolean = obj is SampleObject

    override fun getActions(box: ObjectBox<BufferObject>): List<ContextualizedAction> = actions.withContext(box.obj)

    override fun headerActions(): List<ContextualizedAction> = headerActions.withContext(samples)

    override fun dataFormat(obj: BufferObject): DataFormat = BufferObject.DATA_FORMAT

    companion object : PublicProperty<SampleRegistryPane> by publicProperty("SampleRegistryPane") {
        private val headerActions = collectActions<BufferRegistry> {
            addAction("Toggle copy to samples dir") {
                toggles(
                    { registry -> registry.copyAudioFiles },
                    toggle = { ev, ctx, now ->
                        when {
                            ctx.isEmpty() -> !now
                            now -> YesNoPrompt("Really delete samples from project directory?").showDialog(ev) != true
                            else -> YesNoPrompt("Really copy all samples to project directory?").showDialog(ev) == true
                        }
                    },
                    whenTrue = MaterialDesignP.PACKAGE_VARIANT_CLOSED,
                    whenFalse = MaterialDesignP.PACKAGE_VARIANT
                )
                undoable()
            }
        }

        private val actions = collectActions<BufferObject> {
            addAction("View sample") {
                icon(Evaicons.ACTIVITY)
                executesOn { obj: SampleObject -> obj.showSpectrogram() }
            }
            addAction("Reload") {
                icon(Material2MZ.SYNC)
                description("Reload from file system")
                shortcut("Shift+Alt+S")
                executes(BufferObject::sync)
            }
            addAction("Replace sample contents") {
                icon(MaterialDesignF.FILE_MUSIC_OUTLINE)
                executesOn { obj: SampleObject ->
                    val samples = obj.context.project.buffers
                    val newFile = samples.context[PonticelloFiles].showOpenDialog("*.wav") ?: return@executesOn
                    if (samples.getSample(newFile) != null) return@executesOn
                    obj.loadFile(newFile)
                }
            }
            addAction("Add to score") {
                icon(MaterialDesignC.CONTENT_COPY)
                executesOn { obj: SampleObject, ev ->
                    val duplicator = obj.context[ScoreObjectDuplicator]
                    duplicator.enterDuplicateMode(obj, ev)
                }
            }
        }
    }
}