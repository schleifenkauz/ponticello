package ponticello.ui.registry

import bundles.PublicProperty
import bundles.publicProperty
import bundles.set
import fxutils.actions.ContextualizedAction
import fxutils.actions.collectActions
import fxutils.hasFiles
import fxutils.prompt.YesNoPrompt
import fxutils.prompt.compoundPrompt
import fxutils.registerShortcuts
import fxutils.setFixedWidth
import fxutils.setupDropArea
import hextant.fx.HextantTextField
import javafx.event.Event
import javafx.scene.Node
import javafx.scene.control.Label
import javafx.scene.control.Spinner
import javafx.scene.control.TextField
import javafx.scene.input.DataFormat
import org.kordamp.ikonli.evaicons.Evaicons
import org.kordamp.ikonli.material2.Material2MZ
import org.kordamp.ikonli.materialdesign2.MaterialDesignC
import org.kordamp.ikonli.materialdesign2.MaterialDesignF
import org.kordamp.ikonli.materialdesign2.MaterialDesignP
import ponticello.impl.Logger
import ponticello.impl.parseDecimal
import ponticello.model.obj.AllocatedBufferObject
import ponticello.model.obj.BufferObject
import ponticello.model.obj.SampleObject
import ponticello.model.obj.project
import ponticello.model.project.buffers
import ponticello.model.registry.BufferRegistry
import ponticello.sc.Identifier
import ponticello.ui.actions.undoable
import ponticello.ui.dock.ToolPaneState
import ponticello.ui.launcher.PonticelloFiles
import ponticello.ui.score.ScoreObjectDuplicator
import reaktive.value.fx.asProperty
import reaktive.value.now
import java.io.File

class BufferRegistryPane(private val buffers: BufferRegistry) : ObjectRegistryPane<BufferObject>(buffers) {
    override val title: String
        get() = "Buffers"

    override val headerActions: List<ContextualizedAction> = registryActions.withContext(buffers)

    override fun defaultState(): ToolPaneState = ToolPaneState.docked(ToolPaneState.Side.RIGHT)

    override fun afterSetup() {
        listView.itemsScrollPane.setupDropArea({ db -> db.hasFiles("wav") }, { ev ->
            for (file in ev.dragboard.files) {
                buffers.getOrAdd(file)
            }
        })
        buffers.context[BufferRegistryPane] = this
    }

    override fun addObject(ev: Event?) {
        val obj = loadNewSample { file -> Identifier.truncate(file.nameWithoutExtension) } ?: return
        registry.add(obj)
    }

    private fun loadNewSample(name: (File) -> String): SampleObject? {
        val file = buffers.context[PonticelloFiles].showOpenDialog("*.wav") ?: return null
        if (buffers.getSample(file) != null) return null
        val sample = SampleObject.create(buffers.context.project, name(file), file)
        return sample
    }

    public override fun createNewObject(name: String, ev: Event?): BufferObject? {
        return compoundPrompt("Configure buffer $name") {
            val channelsSpinner = Spinner<Int>(1, 12, 2) named "Channels"
            val durationField = TextField() named "Duration (s)"
            onConfirm {
                val channels = channelsSpinner.value
                val duration = durationField.text.parseDecimal() ?: return@onConfirm null
                AllocatedBufferObject.create(name, channels, duration)
            }
        }.showDialog(this)
    }

    override fun getItemContent(obj: BufferObject): List<Node> {
        obj as AllocatedBufferObject
        val channelsSpinner = Spinner<Int>(1, 12, obj.channels.now).setFixedWidth(70.0)
        channelsSpinner.valueFactory.valueProperty().bindBidirectional(obj.channels.asProperty())
        val durationInput = HextantTextField(obj.duration().now.toString())
        durationInput.setOnAction { syncBuffer(obj, durationInput) }
        durationInput.focusedProperty().addListener { _, _, focused ->
            if (!focused) {
                resetDurationInput(durationInput, obj)
            }
        }
        durationInput.registerShortcuts {
            on("ESCAPE") {
                resetDurationInput(durationInput, obj)
            }
        }
        durationInput.promptText = "Number of frames"
        durationInput.id = "duration-input"
        val xLabel = Label("x")
        return listOf(channelsSpinner, xLabel, durationInput)
    }

    private fun resetDurationInput(durationInput: HextantTextField, obj: BufferObject) {
        durationInput.text = obj.duration().now.toString()
    }

    override fun getActions(box: ObjectBox<BufferObject>): List<ContextualizedAction> = when (box.obj) {
        is AllocatedBufferObject -> allocatedBufferActions.withContext(box)
        is SampleObject -> sampleActions.withContext(box.obj)
    }

    override fun dataFormat(obj: BufferObject): DataFormat = BufferObject.DATA_FORMAT

    companion object: PublicProperty<BufferRegistryPane> by publicProperty("BufferRegistryPane") {
        private val allocatedBufferActions = collectActions<ObjectBox<BufferObject>> {
            addAction("View buffer contents") {
                icon(Evaicons.ACTIVITY)
                executes { box -> box.obj.plotBuffer() }
            }
            addAction("Reallocate") {
                icon(Material2MZ.SYNC)
                shortcut("Ctrl+U")
                executes { box -> sync(box) }
            }
        }

        private fun sync(box: ObjectBox<BufferObject>) {
            val durationInput = box.lookup("#duration-input") as TextField
            syncBuffer(box.obj as AllocatedBufferObject, durationInput)
        }

        private fun syncBuffer(buffer: AllocatedBufferObject, durationInput: TextField) {
            val n = durationInput.text.parseDecimal()
            if (n == null) {
                Logger.warn("Not a valid number of frames specified.", Logger.Category.Buffers)
                return
            }
            buffer.duration.set(n)
            buffer.sync()
        }

        private val sampleActions = collectActions<BufferObject> {
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

        private val registryActions = collectActions<BufferRegistry> {
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
    }
}