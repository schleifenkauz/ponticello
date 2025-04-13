package xenakis.ui.launcher

import bundles.PublicProperty
import bundles.createBundle
import bundles.publicProperty
import bundles.set
import fxutils.*
import fxutils.actions.ActionBar
import fxutils.actions.ContextualizedAction
import fxutils.actions.SelectorBar
import fxutils.actions.registerActions
import hextant.command.line.CommandLineControl
import hextant.command.line.CommandLinePopup
import hextant.context.Properties
import hextant.context.SelectionDistributor
import hextant.core.view.EditorControl
import hextant.fx.handleCommands
import hextant.fx.initHextantScene
import hextant.undo.UndoManager
import hextant.undo.historyShortcuts
import javafx.application.Platform
import javafx.scene.Scene
import javafx.scene.control.Label
import javafx.scene.control.SplitPane
import javafx.scene.layout.*
import javafx.stage.Screen
import javafx.stage.StageStyle
import reaktive.Observer
import reaktive.value.now
import xenakis.impl.Logger
import xenakis.model.Settings
import xenakis.model.obj.ParameterizedObject
import xenakis.model.player.PlaybackManager
import xenakis.model.project.*
import xenakis.sc.client.SuperColliderClient
import xenakis.ui.actions.*
import xenakis.ui.flow.FlowPane
import xenakis.ui.impl.makeSubWindow
import xenakis.ui.launcher.XenakisApp.Companion.primaryStage
import xenakis.ui.midi.ContextualMidiReceiver
import xenakis.ui.midi.ParameterControlsMidiContext
import xenakis.ui.misc.*
import xenakis.ui.registry.ControlBusRegistryPane
import xenakis.ui.registry.ProcessDefRegistryPane
import xenakis.ui.registry.SampleRegistryPane
import xenakis.ui.registry.SynthDefRegistryPane
import xenakis.ui.score.ScoreObjectSelectionManager
import xenakis.ui.score.ScoreView

class XenakisMainActivity(val project: XenakisProject) : Activity() {
    init {
        project.context[XenakisMainActivity] = this
    }

    val toolSelector = SelectorBar(project.context, Tool.entries, Tool.Pointer, "large-icon-button")
        .styleClass("toolbar-part")

    val synthDefsPane = SynthDefRegistryPane(project.instruments)
    val synthDefsWindow = makeSubWindow(synthDefsPane, "Instruments", context, SubWindow.Type.ToolWindow)

    val processDefsPane = ProcessDefRegistryPane(project[PROCESS_DEFS])
    val processDefsWindow = makeSubWindow(processDefsPane, "Process Definitions", context, SubWindow.Type.ToolWindow)

    private val busRegistryPane = ControlBusRegistryPane(project.busses)
    val busesWindow = makeSubWindow(busRegistryPane, "Busses", context, SubWindow.Type.Undecorated)

    private val samplesPane = SampleRegistryPane(project.samples)
    val samplesWindow = makeSubWindow(samplesPane, "Samples", context, SubWindow.Type.Undecorated)

    val logWindow = makeSubWindow(LogPane(Logger), "Log", context, SubWindow.Type.Undecorated)

    val settingsWindow = makeSubWindow(SettingsPane(context[Settings], context), "Settings", context)

    val flowPaneWindow: SubWindow

    val serverTreeCodeWindow: SubWindow
    val serverSetupCodeWindow: SubWindow

    val scoreView: ScoreView

    private val observer: Observer

    private val detailPane = VBox(10.0).apply {
        styleClass("tool-pane")
        children.add(Label("Object details") styleClass "heading")
    }

    val playback: PlaybackManager

    val shellWindow = SuperColliderShellController.createShellWindow(context)

    override val context get() = project.context

    private val launcher get() = context[XenakisLauncher]

    val mode: Mode

    init {
        val largeScreenAvailable = Screen.getScreens().any { s -> s.bounds.width > 3000 }
        mode = if (largeScreenAvailable) Mode.Desktop else Mode.Laptop

        context[XenakisMainActivity] = this
        context[HelpBrowser] = HelpBrowser()
        settingsWindow.width = 1000.0
        settingsWindow.height = 1000.0

        scoreView = ScoreView(project.score, project.context)
        project.context[ScoreObjectSelectionManager] = ScoreObjectSelectionManager(project.context, scoreView)
        scoreView.initialize()

        val flowPane = FlowPane(project.flows)
        flowPane.setPrefSize(1000.0, 1000.0)
        flowPaneWindow = makeSubWindow(flowPane, "Audio flows", context)

        synthDefsWindow.scene.initHextantScene(context)
        processDefsWindow.scene.initHextantScene(context)

        val (serverSetup, serverTree) = project[SETUP_CODE]
        serverSetupCodeWindow = makeSubWindow(serverSetup.control, "ServerSetup", context)
        serverSetupCodeWindow.scene.initHextantScene(context)
        serverSetupCodeWindow.resize(500.0, 500.0)

        serverTreeCodeWindow = makeSubWindow(serverTree.control, "ServerTree", context)
        serverTreeCodeWindow.scene.initHextantScene(context)
        serverTreeCodeWindow.resize(500.0, 500.0)

        playback = PlaybackManager(scoreView, project.flows)
        context[PlaybackManager] = playback

        observer = scoreView.selector.focusedView.observe { _, _, focusedView ->
            if (detailPane.children.size == 2) {
                detailPane.children.removeAt(1)
            }
            if (focusedView != null) {
                detailPane.children.add(focusedView.getDetailPane())
            }
            val obj = focusedView?.instance?.obj
            if (obj is ParameterizedObject) {
                context[ContextualMidiReceiver].setContext(ParameterControlsMidiContext(obj.controls))
            } else {
                context[ContextualMidiReceiver].setContext(null)
            }
        }
    }

    override fun beforeShowing() {
        stage.scene.addGlobalShortcuts()
        stage.initStyle(StageStyle.UNDECORATED)
        ArrowKeys.registerArrowKeys(stage.scene, this)
        stage.title = "Xenakis: ${project.name}"
        stage.isResizable = true
        Platform.runLater {
            if (mode == Mode.Desktop) {
                val screenSize = Screen.getPrimary().bounds
                stage.resize(screenSize.width * 0.75, screenSize.height)
                stage.relocate(0.0, 0.0)
            } else {
                stage.isMaximized = true
            }
        }
    }

    override fun afterShowing() {
        runFXWithTimeout(1000) {
            scoreView.displayWholeScore()
        }
    }

    override fun getLayout(): VBox {
        var mainView: Region = scoreView
        if (mode == Mode.Desktop) {
            val horizontalSplitter = SplitPane(scoreView, detailPane)
            horizontalSplitter.setDividerPositions(0.8)
            mainView = horizontalSplitter
        }
        val toolbar = createToolbar()
        for (box in toolbar.children) HBox.setHgrow(box, Priority.ALWAYS)
        VBox.setVgrow(mainView, Priority.ALWAYS)
        val layout = VBox(toolbar, mainView)
        val context = project.context
        val commandLinePopup = CommandLinePopup(
            context,
            context[Properties.localCommandLine],
            arguments = createBundle { set(CommandLineControl.HISTORY_ITEMS, 0) })
        layout.registerShortcuts {
            handleCommands(project, context, context[Properties.globalCommandLine])
            on("Alt+SPACE") {
                val focusedView = context[SelectionDistributor].focusedView.now
                if (focusedView is EditorControl<*>) {
                    val point = focusedView.localToScreen(0.0, 0.0) ?: return@on
                    commandLinePopup.show(context[primaryStage], point.x, point.y)
                }
            }
            historyShortcuts(context[UndoManager])
        }
        return layout
    }

    private fun createContextBar(): ActionBar {
        val selector = context[ScoreObjectSelectionManager]
        val context = ObjectActionContext.SingleObjectContext(selector)
        val actions = ObjectActions.singleObjectActions.withContext(context) +
                ObjectActions.multiObjectActions.withContext(context)
        return toolbarPart(actions)
    }

    private fun toolbarPart(actions: List<ContextualizedAction>) =
        ActionBar(actions, buttonStyle = "large-icon-button").styleClass("toolbar-part")

    private fun createToolbar(): Pane {
        return BorderPane().apply {
            left = HBox(
                10.0,
                toolbarPart(ProjectActions.withContext(launcher)),
                toolbarPart(UndoRedoActions.withContext(context[UndoManager])),
                toolbarPart(PlaybackActions.withContext(playback)),
                InteractionConfig(project.settings),
                toolSelector,
            )
            center = HBox(infiniteSpace(), createContextBar(), infiniteSpace())
            val toolWindowActions = ToolWindowActions.withContext(this@XenakisMainActivity)
            val serverActions = ServerActions.withContext(project)
            right = HBox(
                toolbarPart(toolWindowActions + serverActions),
                hspace(50.0),
                toolbarPart(QuitAction.withContext(launcher))
            )
        } styleClass "toolbar"
    }

    private fun Scene.addGlobalShortcuts() {
        registerShortcuts {
            registerActions(ProjectActions.withContext(launcher))
            registerActions(QuitAction.withContext(launcher))
            registerActions(PlaybackActions.withContext(playback))
            registerActions(ScoreNavigationActions.withContext(scoreView))
            registerActions(Tool.entries.map { t -> t.action.withContext(toolSelector) })
            InteractionConfig.addGridRelatedShortcuts(this, project)
            registerActions(ToolWindowActions.withContext(this@XenakisMainActivity))
            val objectCtx = ObjectActionContext.MultiObjectContext(context[ScoreObjectSelectionManager])
            registerActions(ObjectActions.singleObjectActions.withContext(objectCtx))
            registerActions(ObjectActions.multiObjectActions.withContext(objectCtx))
            SelectionRelatedActions.addShortcuts(this, this@XenakisMainActivity)
            registerActions(ServerActions.withContext(project))
        }
    }

    enum class Mode {
        Desktop, Laptop;
    }

    override fun close() {
        context[SuperColliderClient].quit()
        //TODO is there more cleanup to do?
    }

    companion object : PublicProperty<XenakisMainActivity> by publicProperty("XenakisMainScreen")
}