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
import xenakis.impl.Logger
import xenakis.model.ScriptObject
import xenakis.model.Settings
import xenakis.model.flow.AudioFlows
import xenakis.model.flow.NodeTree
import xenakis.model.obj.ParameterizedObject
import xenakis.model.player.ActiveObjectsManager
import xenakis.model.player.Recorder
import xenakis.model.player.ScorePlayer
import xenakis.model.project.*
import xenakis.sc.client.SuperColliderClient
import xenakis.ui.actions.*
import xenakis.ui.flow.AudioFlowPane
import xenakis.ui.impl.makeToolWindow
import xenakis.ui.live.LiveLoopRegistryPane
import xenakis.ui.live.LiveTaskRegistryPane
import xenakis.ui.midi.ContextualMidiReceiver
import xenakis.ui.midi.ParameterControlsMidiContext
import xenakis.ui.misc.*
import xenakis.ui.registry.*
import xenakis.ui.score.*

class XenakisMainActivity(val project: XenakisProject) : Activity() {
    init {
        project.context[XenakisMainActivity] = this
    }

    private val detailPaneManager = DetailPaneManager(project.context)

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

    private val liveLoopsPane = LiveLoopRegistryPane(project[LIVE_LOOPS])
    val liveLoopsWindow = context.makeToolWindow(liveLoopsPane, "Live Loops")

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

    private lateinit var scoreView: NavigableScorePane
    private lateinit var timeCodeView: TimeCodeView
    private lateinit var flowGroupLines: FlowGroupLines

    private lateinit var observer: Observer

    private lateinit var player: ScorePlayer

    val shellWindow = SuperColliderOutputPane.createShellWindow(context)

    override val context get() = project.context

    private val launcher get() = context[XenakisLauncher]

    init {
        context[XenakisMainActivity] = this
        context[HelpBrowser] = HelpBrowser()

        setupMainScoreView()
        setupPlayback()
        showObjectDetailsOnSelection()
    }

    private fun setupMainScoreView() {
        scoreView = NavigableScorePane(project.score, project.context)
        scoreView.initialize()
        timeCodeView = TimeCodeView()
        context[TimeCodeView] = timeCodeView
        context[ScorePane.CURRENT_ROOT] = scoreView
        flowGroupLines = FlowGroupLines(project.flows, scoreView)
        val duplicator = ScoreObjectDuplicator()
        project.context[ScoreObjectDuplicator] = duplicator
        duplicator.registerRootPane(scoreView)
        project.context[ScoreObjectSelectionManager] = ScoreObjectSelectionManager(project.context, scoreView)
    }

    private fun setupPlayback() {
        context[ActiveObjectsManager] = ActiveObjectsManager(project.flows)
        context[NodeTree] = NodeTree(context[SuperColliderClient])
        context[Recorder] = Recorder(context)
        player = ScorePlayer(context)
        player.attachToScoreView(scoreView)
        context[ScorePlayer.CURRENT] = ScorePlayer(context)
        context[AudioFlows].createAllFlows()
    }

    private fun showObjectDetailsOnSelection() {
        observer = context[ScoreObjectSelectionManager].focusedView.observe { _, _, focusedView ->
            detailPaneManager.focused(focusedView)
            val obj = focusedView?.obj
            if (obj is ParameterizedObject) {
                context[ContextualMidiReceiver].setContext(ParameterControlsMidiContext(obj.controls))
            } else {
                context[ContextualMidiReceiver].setContext(null)
            }
        }
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
            scoreView.displayWholeScore()
        }
    }

    override fun getLayout(): VBox {
        val toolbar = createToolbar()
        for (box in toolbar.children) HBox.setHgrow(box, Priority.ALWAYS)
        VBox.setVgrow(scoreView, Priority.ALWAYS)
        return VBox(toolbar, scoreView)
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
                toolbarPart(PlaybackActions.withContext(player)),
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
        registerActions(PlaybackActions.withContext(player))
        registerActions(ScoreNavigationActions.withContext(scoreView))
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