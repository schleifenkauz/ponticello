package ponticello.ui.registry

import fxutils.actions.ContextualizedAction
import fxutils.actions.collectActions
import fxutils.addAfter
import fxutils.controls.IntSpinner
import fxutils.drag.hasFile
import fxutils.prompt.SimpleSearchableListView
import fxutils.prompt.YesNoPrompt
import fxutils.prompt.compoundPrompt
import fxutils.registerShortcuts
import fxutils.undo.UndoManager
import hextant.fx.HextantTextField
import javafx.event.Event
import javafx.scene.Node
import javafx.scene.control.Label
import javafx.scene.control.TextField
import javafx.scene.control.Tooltip
import javafx.scene.input.DataFormat
import javafx.scene.input.DragEvent
import javafx.scene.input.Dragboard
import javafx.scene.input.TransferMode
import org.kordamp.ikonli.Ikon
import org.kordamp.ikonli.evaicons.Evaicons
import org.kordamp.ikonli.material2.Material2AL
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
import ponticello.model.project.PonticelloProject
import ponticello.model.project.buffers
import ponticello.model.registry.BufferRegistry
import ponticello.model.registry.ObjectList
import ponticello.sc.Identifier
import ponticello.ui.actions.undoable
import ponticello.ui.dock.BufferRegistryPaneState
import ponticello.ui.dock.Side
import ponticello.ui.dock.ToolPane
import ponticello.ui.dock.ToolPaneState
import ponticello.ui.impl.getFrom
import ponticello.ui.launcher.PonticelloFiles
import ponticello.ui.score.ScoreObjectDuplicator
import reaktive.value.fx.asObservableValue
import reaktive.value.now
import java.io.File

class BufferRegistryPane(private val buffers: BufferRegistry) : ObjectRegistryPane<BufferObject>(buffers) {
    override val type: Type
        get() = BufferRegistryPane
    override val headerActions: List<ContextualizedAction>
        get() = registryActions.withContext(buffers) + super.headerActions

    private var filter = BufferTypeFilter.All
        set(value) {
            field = value
            listView.refilter()
        }

    private val filterSelector = SimpleSearchableListView(BufferTypeFilter.entries, "Select filter")
        .selectorButton(this::filter)

    override fun defaultState(): ToolPaneState = BufferRegistryPaneState.default()

    override fun doSetup() {
        super.doSetup()
        val state = initialState
        if (state is BufferRegistryPaneState) {
            filter = state.filter
        }
    }

    override fun afterSetup() {
        super.afterSetup()
        header.children.addAfter(searchText, filterSelector)
    }

    override fun saveState(dest: ToolPaneState) {
        super.saveState(dest)
        if (dest is BufferRegistryPaneState) {
            dest.filter = filter
        }
    }

    override fun filter(obj: BufferObject): Boolean = super.filter(obj) && filter.filter(obj)

    override fun acceptedTransferModes(dragboard: Dragboard): Array<TransferMode> {
        if (dragboard.hasContent(dataFormat)) return arrayOf(TransferMode.MOVE)
        if (dragboard.hasFile("wav") && !buffers.has(dragboard.files[0].nameWithoutExtension)) {
            return if (buffers.copyAudioFiles.now) arrayOf(TransferMode.COPY)
            else arrayOf(TransferMode.MOVE)
        }
        return arrayOf()
    }

    override fun getDroppedObject(ev: DragEvent): BufferObject? = when {
        ev.dragboard.hasContent(dataFormat) -> ev.dragboard.getFrom(buffers, dataFormat)
        ev.dragboard.hasFile("wav") -> {
            val file = ev.dragboard.files[0]
            val name = file.nameWithoutExtension
            SampleObject.create(name, file)
        }

        else -> null
    }

    override fun createNewObject(ev: Event?, list: ObjectList<BufferObject>): BufferObject? =
        loadNewSample { file -> Identifier.truncate(file.nameWithoutExtension) }

    private fun loadNewSample(name: (File) -> String): SampleObject? {
        val file = buffers.context[PonticelloFiles].showOpenDialog("*.wav") ?: return null
        if (buffers.getSample(file) != null) return null
        val sample = SampleObject.create(name(file), file)
        return sample
    }

    override fun createNewObject(name: String, ev: Event?): BufferObject? {
        return compoundPrompt("Configure buffer $name") {
            val channelsSpinner = IntSpinner(1, 12, 2).minColumns(2) named "Channels"
            val durationField = TextField() named "Duration (s)"
            onConfirm {
                val channels = channelsSpinner.value()
                val duration = durationField.text.parseDecimal() ?: return@onConfirm null
                AllocatedBufferObject.create(name, channels, duration)
            }
        }.showDialog(this)
    }

    override fun getHeaderContent(obj: BufferObject): List<Node> = when (obj) {
        is AllocatedBufferObject -> {
            val channelsSpinner = IntSpinner(obj.channels, 1, 12).minColumns(2)
                .setupUndo("Buffer Channels", obj.context[UndoManager])
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
            listOf(channelsSpinner, xLabel, durationInput)
        }

        else -> emptyList()
    }

    override fun configureBox(box: ObjectBox<BufferObject>, currentMode: ObjectListView.DisplayMode) {
        val obj = box.obj
        if (obj is SampleObject) {
            box.tooltip = Tooltip()
            box.tooltip.textProperty().bind(obj.filePath().asObservableValue())
        }
    }

    private fun resetDurationInput(durationInput: HextantTextField, obj: BufferObject) {
        durationInput.text = obj.duration().now.toString()
    }

    override fun getActions(box: ObjectBox<BufferObject>): List<ContextualizedAction> = when (box.obj) {
        is AllocatedBufferObject -> allocatedBufferActions.withContext(box)
        is SampleObject -> sampleActions.withContext(box.obj)
    }

    override val dataFormat: DataFormat
        get() = BufferObject.DATA_FORMAT

    enum class BufferTypeFilter {
        All, Allocated, Samples;

        fun filter(obj: BufferObject): Boolean = when (this) {
            All -> true
            Allocated -> obj is AllocatedBufferObject
            Samples -> obj is SampleObject
        }
    }

    companion object : Type(uid = 7, "Buffers") {
        override val icon: Ikon
            get() = Material2AL.LIBRARY_MUSIC

        override val shortcuts: Array<String>
            get() = arrayOf("F5")

        override val defaultSide: Side
            get() = Side.RIGHT

        override fun createToolPane(project: PonticelloProject): ToolPane = BufferRegistryPane(project.buffers)

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