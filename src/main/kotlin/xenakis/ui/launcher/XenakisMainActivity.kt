package xenakis.ui.launcher

import bundles.PublicProperty
import bundles.publicProperty
import bundles.set
import fxutils.*
import fxutils.actions.registerActions
import hextant.undo.UndoManager
import javafx.geometry.Dimension2D
import javafx.scene.layout.*
import javafx.scene.paint.Color
import javafx.stage.Screen
import javafx.stage.StageStyle
import reaktive.Observer
import reaktive.value.reactiveValue
import xenakis.impl.Logger
import xenakis.model.ScriptObject
import xenakis.model.Settings
import xenakis.model.flow.AudioFlows
import xenakis.model.player.PlaybackMessageListener
import xenakis.model.player.ScorePlayer
import xenakis.model.project.*
import xenakis.sc.client.SuperColliderClient
import xenakis.ui.actions.*
import xenakis.ui.flow.AudioFlowPane
import xenakis.ui.impl.makeToolWindow
import xenakis.ui.live.LiveTaskRegistryPane
import xenakis.ui.misc.*
import xenakis.ui.registry.*
import xenakis.ui.score.*

class XenakisMainActivity(val project: XenakisProject) : Activity() {
    init {
        project.context[XenakisMainActivity] = this
    }

    val mainScoreView: NavigableScorePane = NavigableScorePane(project.score, project.context)

    val synthDefsPane = SynthDefRegistryPane(project.instruments)
    val synthDefsWindow = context.makeToolWindow(
        synthDefsPane, "Instruments",
        defaultSize = Dimension2D(1200.0, 1200.0)
    )

    val processDefsPane = ProcessDefRegistryPane(project[PROCESS_DEFS])
    val processDefsWindow = context.makeToolWindow(
        processDefsPane, "Process Definitions",
        defaultSize = Dimension2D(1200.0, 1200.0)
    )

    private val controlBusPane = ControlBusRegistryPane(project.busses)
    val controlBusWindow = context.makeToolWindow(controlBusPane, "Control Buses")

    private val audioBusPane = AudioBusRegistryPane(project.busses)
    val audioBusWindow = context.makeToolWindow(audioBusPane, "Audio Buses")

    private val samplesPane = SampleRegistryPane(project.buffers)
    val samplesWindow = context.makeToolWindow(samplesPane, "Samples")

    private val buffersPane = AllocatedBufferRegistryPane(project.buffers)
    val buffersWindow = context.makeToolWindow(buffersPane, "Allocated Buffers")

    val patternsPane = GlobalPatternRegistryPane(project.patterns)
    val patternsWindow = context.makeToolWindow(patternsPane, "Patterns")

    private val liveTasksPane = LiveTaskRegistryPane(project[LIVE_TASKS])
    val liveTasksWindow = context.makeToolWindow(liveTasksPane, "Live Tasks")

    val scoreObjectsPane = ScoreObjectRegistryPane(project.objects)
    val scoreObjectsWindow = context.makeToolWindow(scoreObjectsPane, "Score objects")

    val logWindow = context.makeToolWindow(LogPane(Logger), "Log")

    val settingsWindow = context.makeToolWindow(SettingsPane(context[Settings], context), "Settings")

    private val interactionConfig = InteractionConfigBar(project.settings)

    private val flowPane = AudioFlowPane(project.flows)
    val flowPaneWindow = context.makeToolWindow(flowPane, "Audio flows", defaultSize = Dimension2D(2000.0, 800.0))

    val scriptObjectWindows = ScriptObject.Type.entries.associateWith { type ->
        val root = project[type.component].root
        val pane = CodePane(root, ownWindow = true)
        context.makeToolWindow(pane, type.toString(), defaultSize = Dimension2D(500.0, 500.0))
            .also { w -> w.scene.fill = Color.BLACK }
    }

    private val timeCodeView: TimeCodeView = TimeCodeView()
    private val flowGroupLines: FlowGroupLines = FlowGroupLines(project.flows, mainScoreView)

    private lateinit var observer: Observer

    private lateinit var player: ScorePlayer
    private lateinit var playbackMessageListener: PlaybackMessageListener

    val shellWindow = SuperColliderOutputPane.createShellWindow(context)

    override val context get() = project.context

    private val launcher get() = context[XenakisLauncher]

    init {
        context[XenakisMainActivity] = this
        context[HelpBrowser] = HelpBrowser()
        context[DetailPaneManager] = DetailPaneManager(project.context)

        setupMainScoreView()
        setupPlayback()
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
        playbackMessageListener = PlaybackMessageListener(project.objects, project.flows, player)
        context[SuperColliderClient].addListener(playbackMessageListener)
        context[AudioFlows].createAllFlows()
    }

    override fun beforeShowing() {
        primaryStage.scene.registerGlobalShortcuts(context)
        registerMainActivityShortcuts()
        primaryStage.initStyle(StageStyle.UNDECORATED)
        ArrowKeys.registerArrowKeys(primaryStage.scene, context)
        primaryStage.title = "Xenakis: ${project.name}"
        primaryStage.isResizable = true
        val state = project[UI_STATE].windowStates.getOrPut("MainWindow", ::RegularWindowState)
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
                toolbarPart(PlaybackActions.global.withContext(player)),
                hspace(20.0),
                timeCodeView,
                infiniteSpace()
            )
            val toolWindowActions = ToolWindowActions.withContext(this@XenakisMainActivity)
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

    companion object : PublicProperty<XenakisMainActivity> by publicProperty("XenakisMainScreen")
}