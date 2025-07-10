package ponticello.ui.launcher

import bundles.PublicProperty
import bundles.publicProperty
import bundles.set
import fxutils.actions.registerActions
import fxutils.awaitFx
import fxutils.registerShortcuts
import javafx.geometry.Dimension2D
import javafx.scene.input.KeyCombination
import javafx.stage.Screen
import javafx.stage.StageStyle
import ponticello.model.GlobalSettings
import ponticello.model.flow.AudioFlows
import ponticello.model.player.CircularBufferRecorder
import ponticello.model.player.PlaybackMessageListener
import ponticello.model.player.ScorePlayer
import ponticello.model.project.*
import ponticello.sc.client.SuperColliderClient
import ponticello.ui.actions.*
import ponticello.ui.dock.AppLayout
import ponticello.ui.impl.DEFAULT_SCENE_FILL
import ponticello.ui.impl.sceneFill
import ponticello.ui.midi.ContextualMidiReceiver
import ponticello.ui.misc.InteractionConfigBar
import ponticello.ui.score.*
import reaktive.value.reactiveValue

class PonticelloMainActivity(val project: PonticelloProject) : Activity() {
    init {
        project.context[PonticelloMainActivity] = this
    }

    val mainScoreView: NavigableScorePane = NavigableScorePane(project.mainScore, project.context)

    private val timeCodeView: TimeCodeView = TimeCodeView()

    val interactionConfig = InteractionConfigBar(project.uiState)

    private lateinit var player: ScorePlayer
    private lateinit var playbackMessageListener: PlaybackMessageListener

    override val context get() = project.context

    val launcher get() = context[PonticelloLauncher]

    init {
        context[PonticelloMainActivity] = this
        context[FlowGroupManager] = FlowGroupManager(project.flows, mainScoreView)

        setupMainScoreView()
        setupPlayback()
        context[ContextualMidiReceiver].attachGrid(project[LAUNCHER_GRID])
    }

    private val appLayout by lazy { AppLayout(this, project, mainScoreView, interactionConfig, timeCodeView) }

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
        playbackMessageListener = PlaybackMessageListener(
            context[GlobalSettings], project.objects, project.flows
        )
        context[SuperColliderClient].addListener(playbackMessageListener)
        context[AudioFlows].createAllFlows()
        context[CircularBufferRecorder] = CircularBufferRecorder(context, context[SuperColliderClient])
    }

    override fun beforeShowing() {
        primaryStage.fullScreenExitKeyCombination = KeyCombination.NO_MATCH
        primaryStage.scene.registerGlobalShortcuts(context)
        registerMainActivityShortcuts()
        primaryStage.initStyle(StageStyle.UNDECORATED)
        ArrowKeys.registerArrowKeys(primaryStage.scene, context)
        primaryStage.title = "Ponticello: ${project.name}"
        primaryStage.isResizable = true
        primaryStage.sceneFill(DEFAULT_SCENE_FILL)
        val state = project[UI_STATE].getWindowState(WindowState.Reference.ByTitle("MainWindow"), ::RegularWindowState)
        val screenSize = Screen.getPrimary().bounds
        state.applyTo(primaryStage, defaultSize = Dimension2D(screenSize.width, screenSize.height))
    }

    override fun afterShowing() {
        for (toolPane in appLayout.toolPanes()) {
            toolPane.restoreShowing()
        }
        mainScoreView.isVisible = false
        setVisible()
        val displayRange = project[UI_STATE].mainScoreDisplayRange
        if (displayRange == null) mainScoreView.displayWholeScore().awaitFx {
            mainScoreView.isVisible = true
        }
        else mainScoreView.display(displayRange.start, displayRange.endInclusive).awaitFx {
            mainScoreView.isVisible = true
        }
    }

    override fun getLayout() = appLayout

    private fun registerMainActivityShortcuts() = primaryStage.scene.registerShortcuts {
        registerActions(ProjectActions.withContext(launcher))
        registerActions(ScoreNavigationActions.withContext(mainScoreView))
        interactionConfig.addGridRelatedShortcuts(this)
        val objectCtx = ObjectActionContext.MultiObjectContext(context[ScoreObjectSelectionManager])
        registerActions(ScoreObjectActions.all.withContext(objectCtx))
        SelectionRelatedActions.addShortcuts(this, context)
        registerActions(ProjectActions.withContext(launcher))
        registerActions(WindowActions.all.withContext(this@PonticelloMainActivity))
        registerActions(appLayout.actions())
    }

    override fun close() {
        context[SuperColliderClient].quit()
        project[CLOCKS].stopAll()
        //TODO is there more cleanup to do?
    }

    companion object : PublicProperty<PonticelloMainActivity> by publicProperty("PonticelloMainScreen")
}