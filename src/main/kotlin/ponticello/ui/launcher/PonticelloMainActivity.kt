package ponticello.ui.launcher

import bundles.PublicProperty
import bundles.publicProperty
import bundles.set
import fxutils.actions.registerActions
import fxutils.awaitFx
import fxutils.registerShortcuts
import fxutils.runAfterLayout
import javafx.geometry.Dimension2D
import javafx.scene.input.KeyCombination
import javafx.stage.Screen
import javafx.stage.StageStyle
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
import ponticello.ui.misc.InteractionConfigBar
import ponticello.ui.score.FlowGroupManager
import ponticello.ui.score.NavigableScorePane
import ponticello.ui.score.ScoreObjectDuplicator
import ponticello.ui.score.ScoreObjectSelectionManager
import reaktive.value.reactiveValue

class PonticelloMainActivity(val project: PonticelloProject) : Activity() {
    init {
        project.context[PonticelloMainActivity] = this
    }

    val mainScoreView: NavigableScorePane = NavigableScorePane(project.mainScore, project.context)

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
    }

    private val appLayout by lazy { AppLayout(this, project, mainScoreView, interactionConfig) }

    private fun setupMainScoreView() {
        mainScoreView.initialize()
        context[ScoreObjectDuplicator].registerRootPane(mainScoreView)
        project.context[ScoreObjectSelectionManager] = ScoreObjectSelectionManager(project.context, mainScoreView)
    }

    private fun setupPlayback() {
        player = ScorePlayer.create(
            project.mainScore, mainScoreView.playHead,
            loopingActivated = reactiveValue(false), quantization = null
        )
        context[ScorePlayer.MAIN] = player
        playbackMessageListener = PlaybackMessageListener(
            project[PLAYBACK_SETTINGS], project.objects, project.flows
        )
        context[SuperColliderClient].addListener(playbackMessageListener)
        context[AudioFlows].createAllFlows()
        context[CircularBufferRecorder] = CircularBufferRecorder(context, context[SuperColliderClient])
    }

    override fun beforeShowing() {
        primaryStage.fullScreenExitKeyCombination = KeyCombination.NO_MATCH
        primaryStage.scene.registerGlobalShortcuts(context)
        val objectCtx = ObjectActionContext.MultiObjectContext(context[ScoreObjectSelectionManager])
        primaryStage.scene.registerShortcuts {
            registerActions(ScoreObjectActions.all.withContext(objectCtx))
            registerActions(ScoreActions.withContext(mainScoreView))
        }
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
        val displayRange = project[UI_STATE].mainScoreDisplayRange?.takeIf { !it.isEmpty() }
        runAfterLayout {
            appLayout.restorePaneSizes()
            runAfterLayout {
                if (displayRange == null) mainScoreView.displayWholeScore().awaitFx {
                    mainScoreView.isVisible = true
                } else mainScoreView.display(displayRange.start, displayRange.endInclusive).awaitFx {
                    mainScoreView.isVisible = true
                }
            }
        }
    }

    override fun getLayout() = appLayout

    private fun registerMainActivityShortcuts() = primaryStage.scene.registerShortcuts {
        registerActions(ProjectActions.withContext(project))
        registerActions(LauncherActions.all.withContext(launcher))
        registerActions(ScoreNavigationActions.withContext(mainScoreView))
        interactionConfig.addGridRelatedShortcuts(this)
        SelectionRelatedActions.addShortcuts(this, context)
        registerActions(WindowActions.all.withContext(this@PonticelloMainActivity))
        registerActions(appLayout.actions())
    }

    override fun close() {
        context[SuperColliderClient].quit()
        project[CLOCKS].stopAll()
    }

    companion object : PublicProperty<PonticelloMainActivity> by publicProperty("PonticelloMainScreen")
}