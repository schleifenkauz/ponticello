package ponticello.ui.live

import fxutils.actions.*
import fxutils.bindPseudoClassState
import fxutils.pad
import fxutils.styleClass
import fxutils.undo.ToggleEdit
import fxutils.undo.UndoManager
import hextant.core.editor.defaultState
import hextant.serial.EditorRoot
import javafx.event.Event
import javafx.scene.Parent
import javafx.scene.control.Button
import javafx.scene.input.DataFormat
import javafx.scene.input.DragEvent
import javafx.scene.input.Dragboard
import javafx.scene.input.TransferMode
import javafx.scene.layout.BorderPane
import org.kordamp.ikonli.Ikon
import org.kordamp.ikonli.materialdesign2.*
import ponticello.model.live.LiveObject
import ponticello.model.live.LiveObjectRegistry
import ponticello.model.live.LiveScoreObject
import ponticello.model.live.LiveTaskObject
import ponticello.model.obj.SuperColliderObject
import ponticello.model.obj.withName
import ponticello.model.player.MeterRegistry
import ponticello.model.project.LIVE_OBJECTS
import ponticello.model.project.PonticelloProject
import ponticello.model.project.get
import ponticello.model.registry.ScoreObjectRegistry
import ponticello.model.registry.reference
import ponticello.model.score.ScoreObject
import ponticello.sc.editor.CodeBlockEditor
import ponticello.ui.actions.undoable
import ponticello.ui.dock.*
import ponticello.ui.impl.getFrom
import ponticello.ui.misc.CodePane
import ponticello.ui.registry.ObjectBox
import ponticello.ui.registry.ObjectListView
import ponticello.ui.registry.ObjectListView.DisplayMode
import ponticello.ui.registry.ObjectRegistryPane
import ponticello.ui.registry.SimpleRegistrySelectorPrompt
import ponticello.ui.score.ScoreObjectView
import ponticello.ui.score.ScoreObjectViewPane
import ponticello.ui.score.SingleObjectScorePane
import reaktive.value.binding.and
import reaktive.value.binding.map
import reaktive.value.binding.not
import reaktive.value.now
import reaktive.value.reactiveValue
import reaktive.value.toggle

class LiveObjectRegistryPane(
    registry: LiveObjectRegistry
) : ObjectRegistryPane<LiveObject>(registry, LIVE_OBJECTS.serializer) {
    override val type: Type
        get() = LiveObjectRegistryPane

    override val dataFormat: DataFormat
        get() = LiveObject.DATA_FORMAT

    override val supportedModes: Collection<DisplayMode>
        get() = setOf(DisplayMode.Collapsable)

    override fun defaultState(): ToolPaneState = ListToolPaneState.docked

    override fun getActions(box: ObjectBox<LiveObject>): List<ContextualizedAction> = actions.withContext(box.obj)

    override val headerActions: List<ContextualizedAction>
        get() = LiveObjectRegistryPane.headerActions.withContext(this) + super.headerActions

    override fun getContent(obj: LiveObject, box: ObjectBox<LiveObject>): Parent = when (obj) {
        is LiveTaskObject -> {
            val actions =
                if (box.currentMode == DisplayMode.SubWindow) actions.withContext(obj)
                else emptyList()
            CodePane(obj.code, actions, ownWindow = box.currentMode == DisplayMode.SubWindow)
        }

        is LiveScoreObject -> {
            val scorePane = SingleObjectScorePane(
                obj.scoreObject, obj.context, obj.playHead!!,
                paintGrid = reactiveValue(false), playHeadStyle = "live-object-play-head",
            )
            scorePane.isDisable = true
            scorePane.prefHeight = 30.0
            scorePane.maxHeight = 30.0
            scorePane.initialize()
            BorderPane(scorePane).pad(3.0)
        }
    }

    override fun acceptedTransferModes(dragboard: Dragboard): Array<TransferMode> = when {
        dragboard.hasContent(ScoreObject.DATA_FORMAT) -> arrayOf(TransferMode.LINK)
        else -> super.acceptedTransferModes(dragboard)
    }

    override fun getDroppedObjects(ev: DragEvent, targetView: ObjectListView<LiveObject>): List<LiveObject> {
        return when {
            ev.dragboard.hasContent(ScoreObject.DATA_FORMAT) -> {
                val obj = ev.dragboard.getFrom(context[ScoreObjectRegistry], ScoreObject.DATA_FORMAT)
                    ?: return emptyList()
                val view = ev.gestureSource as? ScoreObjectView
                val liveObject = LiveScoreObject(obj.reference())
                if (view != null) {
                    liveObject.absoluteScoreY.now = view.absolutePosition.y
                    liveObject.inferQuantizationFrom(view.absolutePosition, view.context)
                }
                listOf(liveObject)
            }

            else -> super.getDroppedObjects(ev, targetView)
        }
    }

    override fun configureBox(box: ObjectBox<LiveObject>, currentMode: DisplayMode) {
        if (box.header.children.firstOrNull() !is Button) {
            box.header.children.add(0, playPauseAction.withContext(box.obj).makeButton("medium-icon-button"))
        }
        box.registerShortcuts(listOf(playPauseAction.withContext(box.obj)))
        box.styleClass("live-object-box")
        box.userData = box.bindPseudoClassState("playing", box.obj.isPlaying)
    }

    override fun createNewObject(name: String, ev: Event?): LiveTaskObject =
        LiveTaskObject(EditorRoot(CodeBlockEditor().defaultState())).withName(name)

    companion object : Type(uid = 10, "Live Objects") {
        override val icon: Ikon
            get() = MaterialDesignP.PLAYLIST_PLAY

        override val defaultSide: Side
            get() = Side.LEFT

        override fun createToolPane(project: PonticelloProject): ToolPane =
            LiveObjectRegistryPane(project[LIVE_OBJECTS])

        val configureQuantizationAction = action<LiveObject>("Configure quantization") {
            enableWhen { item -> item.isScheduled.not() }
            description("Quantization (hold shift to configure)")
            icon(MaterialDesignM.METRONOME)
            toggleState { item -> item.quantization.enableQuantization }
            executes { item, ev ->
                if (ev.isShiftDown()) {
                    if (item.quantization.meter.now.isResolved.now.not()) {
                        val meter = SimpleRegistrySelectorPrompt(item.context[MeterRegistry], "Select meter")
                            .showPopup(ev) ?: return@executes
                        item.quantization.meter.set(meter.reference())
                    }
                    val copy = item.quantization.copy()
                    copy.initialize(item.context)
                    copy.enableQuantization.set(true)
                    QuantizationConfigDialog(copy, "Configure live loop '${item.name.now}")
                        .showDialog(ev) ?: return@executes
                    item.quantization.update(copy)
                } else {
                    item.quantization.enableQuantization.toggle()
                    item.context[UndoManager].record(
                        ToggleEdit("Toggle quantization", item.quantization.enableQuantization)
                    )
                }
            }
        }

        val toggleLoopingAction = action<LiveScoreObject>("Toggle looping") {
            toggles(
                { item -> item.loopingActivated },
                whenFalse = MaterialDesignR.REPEAT_OFF,
                whenTrue = MaterialDesignR.REPEAT,
            )
            undoable()
        }

        val playPauseAction = action<LiveObject>("Play/pause") {
            icon { obj ->
                obj.isScheduled.map { scheduled ->
                    if (scheduled) MaterialDesignP.PAUSE
                    else MaterialDesignP.PLAY
                }
            }
            toggleState { obj -> obj.isScheduled and obj.isPlaying.not() }
            shortcut("Ctrl+SPACE")
            executes { obj -> obj.toggle() }
        }

        val actions = collectActions<LiveObject> {
            addAction("Show in object view") {
                icon(MaterialDesignE.EYE)
                executesOn<LiveScoreObject> { obj ->
                    val pane = context[AppLayout].get<ScoreObjectViewPane>()
                    pane.showContent(obj)
                }
            }
            add(configureQuantizationAction)
            add(toggleLoopingAction) { obj -> obj as? LiveScoreObject }
            addAction("Sync") {
                icon(MaterialDesignS.SYNC)
                shortcut("Ctrl+U")
                applicableIf { obj -> obj is SuperColliderObject }
                executes { obj ->
                    if (obj is SuperColliderObject) {
                        obj.sync()
                    }
                }
            }
            addAction("Reset") {
                icon(MaterialDesignS.STOP)
                executes { obj -> obj.reset() }
            }
        }

        val headerActions = collectActions<LiveObjectRegistryPane> {
            addAction("Stop all") {
                icon(MaterialDesignS.STOP)
                executes { pane ->
                    pane.registry.forEach { obj ->
                        if (obj.isScheduled.now) {
                            obj.reset()
                        }
                    }
                }
            }
        }
    }
}