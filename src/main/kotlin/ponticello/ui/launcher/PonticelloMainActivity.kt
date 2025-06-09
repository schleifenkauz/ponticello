package ponticello.ui.launcher

import bundles.PublicProperty
import bundles.publicProperty
import bundles.set
import fxutils.actions.registerActions
import fxutils.registerShortcuts
import fxutils.undo.UndoManager
import javafx.geometry.Dimension2D
import javafx.stage.Screen
import javafx.stage.StageStyle
import ponticello.model.flow.AudioFlow
import ponticello.model.flow.AudioFlows
import ponticello.model.player.CircularBufferRecorder
import ponticello.model.player.PlaybackMessageListener
import ponticello.model.player.ScorePlayer
import ponticello.model.project.*
import ponticello.sc.client.SuperColliderClient
import ponticello.ui.actions.*
import ponticello.ui.dock.AppLayout
import ponticello.ui.flow.AudioFlowPane
import ponticello.ui.impl.DEFAULT_SCENE_FILL
import ponticello.ui.impl.makeToolWindow
import ponticello.ui.impl.sceneFill
import ponticello.ui.midi.ContextualMidiReceiver
import ponticello.ui.misc.HelpBrowser
import ponticello.ui.misc.InteractionConfigBar
import ponticello.ui.misc.SuperColliderOutputPane
import ponticello.ui.registry.ObjectListView
import ponticello.ui.score.*
import reaktive.value.reactiveValue

class PonticelloMainActivity(val project: PonticelloProject) : Activity() {
    init {
        project.context[PonticelloMainActivity] = this
    }

    private val mainScoreView: NavigableScorePane = NavigableScorePane(project.mainScore, project.context)

    private val timeCodeView: TimeCodeView = TimeCodeView()

    val interactionConfig = InteractionConfigBar(project.settings)

    private val flowPane by lazy { AudioFlowPane(project.flows) }
    val flowPaneWindow by lazy {
        val window = context.makeToolWindow(flowPane, "Audio flows", defaultSize = Dimension2D(2000.0, 800.0))
            .sceneFill(DEFAULT_SCENE_FILL)
        context[ContextualMidiReceiver].registerMidiContext(window) {
            val selectedGroup = flowPane.listView.selectedBox() ?: return@registerMidiContext null
            val flowListView = selectedGroup.content() as? ObjectListView<*> ?: return@registerMidiContext null
            val selectedFlow = flowListView.selectedObject() as? AudioFlow
            selectedFlow?.midiContext()
        }
        window
    }


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
        context[ContextualMidiReceiver].attachGrid(project[LAUNCHER_GRID])
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
        mainScoreView.displayWholeScore()
    }

    override fun getLayout() = AppLayout(launcher, project, mainScoreView, interactionConfig, timeCodeView)

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