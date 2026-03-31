package ponticello.ui.registry

import fxutils.*
import fxutils.actions.ContextualizedAction
import fxutils.actions.collectActions
import fxutils.controls.IntSpinner
import fxutils.drag.hasFile
import fxutils.drag.hasFiles
import fxutils.prompt.*
import fxutils.undo.UndoManager
import hextant.context.Context
import hextant.fx.HextantTextField
import javafx.scene.Node
import javafx.scene.Parent
import javafx.scene.control.Label
import javafx.scene.control.TextField
import javafx.scene.control.Tooltip
import javafx.scene.image.ImageView
import javafx.scene.input.DataFormat
import javafx.scene.input.DragEvent
import javafx.scene.input.Dragboard
import javafx.scene.input.TransferMode
import javafx.scene.layout.Pane
import org.kordamp.ikonli.Ikon
import org.kordamp.ikonli.evaicons.Evaicons
import org.kordamp.ikonli.material2.Material2AL
import org.kordamp.ikonli.material2.Material2MZ
import org.kordamp.ikonli.materialdesign2.*
import ponticello.impl.Logger
import ponticello.impl.parseDecimal
import ponticello.impl.zero
import ponticello.model.obj.project
import ponticello.model.project.PonticelloProject
import ponticello.model.project.buffers
import ponticello.model.score.MeterObject
import ponticello.model.server.AllocatedBufferObject
import ponticello.model.server.BufferObject
import ponticello.model.server.BufferRegistry
import ponticello.model.server.SampleObject
import ponticello.sc.Identifier
import ponticello.ui.actions.undoable
import ponticello.ui.dock.BufferRegistryPaneState
import ponticello.ui.dock.Side
import ponticello.ui.dock.ToolPane
import ponticello.ui.dock.ToolPaneState
import ponticello.ui.impl.defaultPlacement
import ponticello.ui.launcher.PonticelloFiles
import ponticello.ui.score.ScoreObjectDuplicator
import ponticello.ui.score.TempoGridObjectView
import reaktive.value.binding.binding
import reaktive.value.binding.greaterThanOrEqualTo
import reaktive.value.binding.notEqualTo
import reaktive.value.fx.asObservableValue
import reaktive.value.now
import reaktive.value.reactiveValue

class BufferRegistryPane(private val buffers: BufferRegistry) : ObjectRegistryPane<BufferObject>(buffers) {
    override val type: Type
        get() = BufferRegistryPane
    override val headerActions: List<ContextualizedAction>
        get() = registryActions.withContext(buffers) + super.headerActions

    override val supportedModes: Collection<ObjectListView.DisplayMode>
        get() = setOf(ObjectListView.DisplayMode.Collapsable)

    private var filter = BufferTypeFilter.All
        set(value) {
            field = value
            listView.refilter()
        }

    private val filterSelector = SimpleSelectorPrompt(BufferTypeFilter.entries, "Select filter")
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

    override fun acceptedTransferModes(dragboard: Dragboard): Array<TransferMode> = when {
        dragboard.hasContent(dataFormat) -> arrayOf(TransferMode.MOVE)
        dragboard.hasFile(*SampleObject.SUPPORTED_AUDIO_FORMATS) && !buffers.has(dragboard.files[0].nameWithoutExtension) -> {
            TransferMode.COPY_OR_MOVE
        }

        else -> arrayOf()
    }

    override fun getDroppedObjects(ev: DragEvent, targetView: ObjectListView<BufferObject>): List<BufferObject> = when {
        ev.dragboard.hasFiles(*SampleObject.SUPPORTED_AUDIO_FORMATS) -> {
            val files = ev.dragboard.files
            files.map { file ->
                val name = Identifier.truncate(file.nameWithoutExtension)
                SampleObject.create(name, file)
            }
        }

        else -> super.getDroppedObjects(ev, targetView)
    }

    override fun createNewObject(name: String, promptPlacement: PromptPlacement?): BufferObject? {
        return compoundPrompt("Configure buffer $name", labelWidth = 100.0) {
            val channelsSpinner = IntSpinner(1, 12, 2).minColumns(2) named "Channels"
            val durationField = TextField() named "Duration (s)"
            onConfirm {
                val channels = channelsSpinner.value()
                val duration = durationField.text.parseDecimal() ?: return@onConfirm null
                AllocatedBufferObject.create(name, channels, duration)
            }
        }.showDialog(promptPlacement ?: PromptPlacement.RelativeTo(this))
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

        is SampleObject -> {
            val meter = obj.meter
            val meterInfo = binding(meter.beatsPerMinute, meter.beatsPerBar, meter.ticksPerBeat) { bpm, bpb, tpb ->
                "$bpm bpm ($bpb x $tpb)"
            }
            val meterLabel = label(meterInfo)
            meterLabel.managedProperty().bind(meter.beatsPerMinute.notEqualTo(zero).asObservableValue())
            listOf(meterLabel)
        }
    }

    override fun getContent(obj: BufferObject, box: ObjectBox<BufferObject>): Parent? {
        return when (obj) {
            is AllocatedBufferObject -> null
            is SampleObject -> {
                val pane = Pane().alwaysHGrow()
                val view = ImageView()
                view.imageProperty().bind(obj.spectrogramImage.asObservableValue())
                view.fitHeight = SPECTROGRAM_HEIGHT
                view.fitWidthProperty().bind(pane.widthProperty())
                view.isPreserveRatio = false
                view.managedProperty().bind(view.imageProperty().isNotNull)
                val notFoundLabel = Label("Spectrogram file not found...")
                notFoundLabel.managedProperty().bind(view.imageProperty().isNull)
                pane.children.addAll(view, notFoundLabel)
                pane
            }
        }
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

    private class MeterConfigPrompt(
        private val sample: SampleObject,
        private val context: Context,
        title: String,
    ) : CompoundPrompt<Unit>(title) {
        private val meter = if (sample.meter.isNone()) MeterObject.createDefault() else sample.meter.copy()
        private val firstBeatField = TextField(sample.firstBeat.now.toString()) styleClass "sleek-text-field"
        private val bpmSpinner = TempoGridObjectView.setupMeterConfig(meter, content, undoManager = null)

        init {
            addItem("First beat", firstBeatField)
            confirmButton.disableProperty().bind(
                firstBeatField.textProperty().map { t -> t.parseDecimal() == null }
            )
        }

        override fun onReceiveFocus() {
            bpmSpinner.requestFocus()
        }

        override fun confirm(): Unit? {
            val firstBeat = firstBeatField.text.parseDecimal() ?: return null
            sample.firstBeat.now = firstBeat
            sample.meter.update(meter, context[UndoManager])
            return Unit
        }
    }

    companion object : Type(uid = 7, "Buffers") {
        override val icon: Ikon
            get() = Material2AL.LIBRARY_MUSIC

        override val shortcut: String
            get() = "F5"

        override val defaultSide: Side
            get() = Side.RIGHT

        override fun createToolPane(project: PonticelloProject): ToolPane = BufferRegistryPane(project.buffers)

        private const val SPECTROGRAM_HEIGHT = 30.0

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
            addAction("Channel mapping") {
                icon(MaterialDesignS.SWAP_HORIZONTAL_VARIANT)
                enableWhen { obj ->
                    if (obj !is SampleObject) reactiveValue(false)
                    else obj.sourceChannels.greaterThanOrEqualTo(2)
                }
                executes { obj: BufferObject, ev ->
                    if (obj !is SampleObject) return@executes
                    val placement = ev?.nextToTarget() ?: obj.context.defaultPlacement
                    SampleChannelMappingPrompt(obj).showDialog(placement)
                }
            }
            addAction("Configure meter") {
                icon(MaterialDesignM.METRONOME)
                executesOn { obj: SampleObject, ev ->
                    val placement = ev?.nextToTarget() ?: obj.context.defaultPlacement
                    MeterConfigPrompt(obj, obj.context, "Edit meter")
                        .showDialog(placement)
                }
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
                    val promptPlacement = ev?.nextToTarget() ?: obj.context.defaultPlacement
                    duplicator.enterDuplicateMode(obj, promptPlacement)
                }
            }
        }

        private val registryActions = collectActions<BufferRegistry> {
            addAction("Toggle copy to samples dir") {
                toggles(
                    { registry -> registry.copyAudioFiles },
                    toggle = { ev, registry, now ->
                        val placement = ev?.nextToTarget() ?: registry.context.defaultPlacement
                        when {
                            registry.isEmpty() -> !now

                            now -> YesNoPrompt("Really delete samples from project directory?")
                                .showDialog(placement) != true

                            else -> YesNoPrompt("Really copy all samples to project directory?")
                                .showDialog(placement) == true
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