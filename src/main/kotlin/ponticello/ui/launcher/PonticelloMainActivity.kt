package ponticello.ui.launcher

import bundles.PublicProperty
import bundles.publicProperty
import bundles.set
import fxutils.*
import fxutils.actions.ActionBar
import fxutils.actions.registerActions
import fxutils.undo.UndoManager
import javafx.geometry.Dimension2D
import javafx.scene.input.TransferMode
import javafx.scene.layout.*
import javafx.stage.Screen
import javafx.stage.StageStyle
import ponticello.impl.Logger
import ponticello.model.ScriptObject
import ponticello.model.Settings
import ponticello.model.flow.AudioFlow
import ponticello.model.flow.AudioFlows
import ponticello.model.player.CircularBufferRecorder
import ponticello.model.player.PlaybackMessageListener
import ponticello.model.player.ScorePlayer
import ponticello.model.project.*
import ponticello.sc.client.SuperColliderClient
import ponticello.ui.actions.*
import ponticello.ui.flow.AudioFlowPane
import ponticello.ui.impl.makeToolWindow
import ponticello.ui.live.LauncherGridPane
import ponticello.ui.live.LiveTaskRegistryPane
import ponticello.ui.midi.ContextualMidiReceiver
import ponticello.ui.midi.ControlBusesMidiReceiver
import ponticello.ui.misc.*
import ponticello.ui.registry.*
import ponticello.ui.score.*
import reaktive.value.reactiveValue

class PonticelloMainActivity(val project: PonticelloProject) : Activity() {
    init {
        project.context[PonticelloMainActivity] = this
    }

    private val mainScoreView: NavigableScorePane = NavigableScorePane(project.mainScore, project.context)

    private val synthDefsPane by lazy { SynthDefRegistryPane(project.instruments) }
    val synthDefsWindow by lazy {
        context.makeToolWindow(
            synthDefsPane, "Instruments",
            defaultSize = Dimension2D(1200.0, 1200.0)
        )
    }

    fun synthDefsPane(): SynthDefRegistryPane {
        synthDefsWindow
        return synthDefsPane
    }

    private val processDefsPane by lazy { ProcessDefRegistryPane(project[PROCESS_DEFS]) }
    val processDefsWindow by lazy {
        context.makeToolWindow(
            processDefsPane, "Process Definitions",
            defaultSize = Dimension2D(1200.0, 1200.0)
        )
    }

    fun processDefsPane(): ProcessDefRegistryPane {
        processDefsWindow
        return processDefsPane
    }

    private val controlBusPane by lazy { ControlBusRegistryPane(project.busses) }
    val controlBusWindow by lazy { context.makeToolWindow(controlBusPane, "Control Buses") }

    private val audioBusPane by lazy { AudioBusRegistryPane(project.busses)}
    val audioBusWindow by lazy { context.makeToolWindow(audioBusPane, "Audio Buses") }

    private val samplesPane by lazy { SampleRegistryPane(project.buffers) }
    val samplesWindow by lazy { context.makeToolWindow(samplesPane, "Samples") }

    private val buffersPane by lazy { AllocatedBufferRegistryPane(project.buffers) }
    val buffersWindow by lazy { context.makeToolWindow(buffersPane, "Allocated Buffers") }

    private val patternsPane by lazy { GlobalPatternRegistryPane(project.patterns) }
    val patternsWindow by lazy { context.makeToolWindow(patternsPane, "Patterns") }

    fun patternsPane(): GlobalPatternRegistryPane {
        patternsWindow
        return patternsPane
    }

    private val liveTasksPane by lazy { LiveTaskRegistryPane(project[LIVE_TASKS]) }
    val liveTasksWindow by lazy { context.makeToolWindow(liveTasksPane, "Live Tasks") }

    private val clocksPane by lazy { ClockRegistryPane(project[CLOCKS]) }
    val clocksWindow by lazy { context.makeToolWindow(clocksPane, "Clocks") }

    private val gridPane by lazy { LauncherGridPane(project[LAUNCHER_GRID]) }
    val launcherGridWindow by lazy {
        context.makeToolWindow(gridPane, "Launcher Grid").also { w ->
            w.sizeToScene()
            w.isResizable = false
        }
    }

    private val scoreObjectsPane by lazy { ScoreObjectRegistryPane(project.objects) }
    val scoreObjectsWindow by lazy { context.makeToolWindow(scoreObjectsPane, "Score objects") }

    fun scoreObjectsPane(): ScoreObjectRegistryPane {
        scoreObjectsWindow
        return scoreObjectsPane
    }

    val logWindow by lazy { context.makeToolWindow(LogPane(Logger), "Log") }

    val settingsWindow by lazy { context.makeToolWindow(SettingsPane(context[Settings], context), "Settings") }

    val interactionConfig = InteractionConfigBar(project.settings)

    lateinit var playerBar: ActionBar
        private set

    private val flowPane by lazy { AudioFlowPane (project.flows) }
    val flowPaneWindow by lazy { context.makeToolWindow(flowPane, "Audio flows", defaultSize = Dimension2D(2000.0, 800.0)) }

    val scriptObjectWindows = ScriptObject.Type.entries.associateWith { type ->
        val root = project[type.component].root
        val pane = CodePane(root, ownWindow = true)
        context.makeToolWindow(pane, type.toString(), defaultSize = Dimension2D(500.0, 500.0))
    }

    private val timeCodeView: TimeCodeView = TimeCodeView()

    private lateinit var player: ScorePlayer
    private lateinit var playbackMessageListener: PlaybackMessageListener

    val shellWindow = SuperColliderOutputPane.createShellWindow(context)

    override val context get() = project.context

    private val launcher get() = context[PonticelloLauncher]

    init {
        context[PonticelloMainActivity] = this
        context[HelpBrowser] = HelpBrowser()
        context[DetailPaneManager] = DetailPaneManager(project.context)
        context[FlowGroupManager] = FlowGroupManager(project.flows, mainScoreView)

        setupMainScoreView()
        setupPlayback()
        registerMidiContexts()
    }

    private fun registerMidiContexts() {
        val receiver = context[ContextualMidiReceiver]
        receiver.registerMidiContext(controlBusWindow) { ControlBusesMidiReceiver(project.busses) }
        receiver.registerMidiContext(flowPaneWindow) {
            val selectedGroup = flowPane.listView.selectedBox() ?: return@registerMidiContext null
            val flowListView = selectedGroup.content() as? ObjectListView<*> ?: return@registerMidiContext null
            val selectedFlow = flowListView.selectedObject() as? AudioFlow
            selectedFlow?.midiContext()
        }
        receiver.attachGrid(project[LAUNCHER_GRID])
    }

    private fun setupMainScoreView() {
        mainScoreView.initialize()
        context[TimeCodeView] = timeCodeView
        context[ScorePane.CURRENT_ROOT] = mainScoreView
        context[ScoreObjectDuplicator].registerRootPane(mainScoreView)
        project.context[ScoreObjectSelectionManager] = ScoreObjectSelectionManager(project.context, mainScoreView)
    }

    private fun setupPlayback() {
        player = ScorePlayer.create(mainScoreView, loopingActivated = reactiveValue(false))
        context[ScorePlayer.CURRENT] = player
        context[ScorePlayer.MAIN] = player
        playbackMessageListener = PlaybackMessageListener(project.objects, project.flows, player)
        context[SuperColliderClient].addListener(playbackMessageListener)
        context[AudioFlows].createAllFlows()
        playerBar = toolbarPart(PlaybackActions.global.withContext(player))
        val recordBtn = playerBar.getButton(PlaybackActions.toggleRecording)
        recordBtn.setOnDragDetected { ev ->
            val db = recordBtn.startDragAndDrop(TransferMode.COPY)
            db.setContent(mapOf(PlaybackActions.RECORD_BUTTON to "<>"))
            ev.consume()
        }
        context[CircularBufferRecorder] = CircularBufferRecorder(context, context[SuperColliderClient])
    }

    override fun beforeShowing() {
        primaryStage.scene.registerGlobalShortcuts(context)
        registerMainActivityShortcuts()
        primaryStage.initStyle(StageStyle.UNDECORATED)
        ArrowKeys.registerArrowKeys(primaryStage.scene, context)
        primaryStage.title = "Ponticello: ${project.name}"
        primaryStage.isResizable = true
        val state = project[UI_STATE].getWindowState(WindowState.Reference.ByTitle("MainWindow"), ::RegularWindowState)
        val screenSize = Screen.getPrimary().bounds
        state.applyTo(primaryStage, defaultSize = Dimension2D(screenSize.width, screenSize.height))
    }

    override fun afterShowing() {
        runFXWithTimeout(1000) {
            mainScoreView.displayWholeScore()
        }
    }

    override fun getLayout(): VBox {
        val toolbar = createToolbar()
        for (box in toolbar.children) HBox.setHgrow(box, Priority.ALWAYS)
        VBox.setVgrow(mainScoreView, Priority.ALWAYS)
        return VBox(toolbar, mainScoreView)
    }

    private fun createToolbar(): Pane {
        return BorderPane().apply {
            left = HBox(
                10.0,
                toolbarPart(ProjectActions.withContext(launcher)),
                toolbarPart(UndoRedoActions.withContext(context[UndoManager])),
            )
            center = HBox(
                infiniteSpace(),
                interactionConfig,
                hspace(20.0),
                playerBar,
                hspace(20.0),
                timeCodeView,
                infiniteSpace()
            )
            val toolWindowActions = ToolWindowActions.withContext(this@PonticelloMainActivity)
            val serverActions = ServerActions.withContext(project)
            right = HBox(
                toolbarPart(toolWindowActions + serverActions),
                hspace(50.0),
                toolbarPart(QuitAction.withContext(launcher))
            )
        } styleClass "toolbar"
    }

    private fun registerMainActivityShortcuts() = primaryStage.scene.registerShortcuts {
        registerActions(ProjectActions.withContext(launcher))
        registerActions(QuitAction.withContext(launcher))
        registerActions(ScoreNavigationActions.withContext(mainScoreView))
        interactionConfig.addGridRelatedShortcuts(this)
        val objectCtx = ObjectActionContext.MultiObjectContext(context[ScoreObjectSelectionManager])
        registerActions(ObjectActions.all.withContext(objectCtx))
        SelectionRelatedActions.addShortcuts(this, context)
        registerActions(UndoRedoActions.withContext(context[UndoManager]))
    }

    override fun close() {
        context[SuperColliderClient].quit()
        //TODO is there more cleanup to do?
    }

    companion object : PublicProperty<PonticelloMainActivity> by publicProperty("PonticelloMainScreen")
}