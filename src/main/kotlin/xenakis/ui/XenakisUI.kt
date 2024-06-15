package xenakis.ui

import bundles.PublicProperty
import bundles.createBundle
import bundles.publicProperty
import bundles.set
import hextant.command.line.CommandLineControl
import hextant.command.line.CommandLinePopup
import hextant.context.Properties
import hextant.context.SelectionDistributor
import hextant.core.view.EditorControl
import hextant.fx.*
import hextant.undo.UndoManager
import hextant.undo.historyShortcuts
import javafx.application.Platform
import javafx.geometry.Orientation
import javafx.geometry.Pos
import javafx.scene.Scene
import javafx.scene.control.Button
import javafx.scene.control.SplitPane
import javafx.scene.control.Tooltip
import javafx.scene.layout.HBox
import javafx.scene.layout.Pane
import javafx.scene.layout.Priority
import javafx.scene.layout.VBox
import javafx.scene.paint.Color
import javafx.stage.Screen
import javafx.stage.Stage
import org.controlsfx.control.textfield.TextFields
import reaktive.Observer
import reaktive.value.binding.map
import reaktive.value.binding.not
import reaktive.value.forEach
import reaktive.value.fx.asObservableValue
import reaktive.value.now
import reaktive.value.toggle
import xenakis.model.ClonedObject
import xenakis.model.LayoutManager.LayoutAspect
import xenakis.model.Settings
import xenakis.model.SuperColliderObject
import xenakis.model.XenakisProject
import xenakis.ui.ToolSelector.Tool

class XenakisUI(private val stage: Stage, private val controller: XenakisController) : XenakisListener {
    val project get() = controller.currentProject

    val toolSelector = ToolSelector()

    private lateinit var synthDefsPane: InstrumentRegistryPane
    private lateinit var busRegistryPane: BusRegistryPane
    private lateinit var samplesPane: SampleRegistryPane
    private lateinit var groupsPane: GroupRegistryPane
    lateinit var scoreView: ScoreView
        private set
    private lateinit var flowGraphWindow: SubWindow
    private lateinit var globalControlsWindow: SubWindow
    private lateinit var serverTreeCodeWindow: SubWindow
    private lateinit var serverSetupCodeWindow: SubWindow
    private val settingsWindow: Stage

    private lateinit var playBtn: Button
    private lateinit var stopBtn: Button

    lateinit var player: ScorePlayer
    private lateinit var shellWindow: Stage

    private val contextBar = HBox(5.0)
    private lateinit var selectedObjectObserver: Observer

    private var displaysProject = false

    private val context get() = controller.context

    init {
        context[XenakisUI] = this
        contextBar.alwaysHGrow()
        contextBar.centerChildrenVertically()
        context[HelpBrowser] = HelpBrowser(context)
        settingsWindow = SubWindow(SettingsPane(context[Settings], context), "Settings", context)
        settingsWindow.width = 1000.0
        settingsWindow.height = 1000.0
        stage.scene = Scene(Pane())
        stage.scene.addGlobalShortcuts()
        stage.scene.initHextantScene(context)
    }

    override fun displayProject(project: XenakisProject) {
        synthDefsPane = InstrumentRegistryPane(project.instruments)
        context[InstrumentRegistryPane] = synthDefsPane
        busRegistryPane = BusRegistryPane(project.busses)
        samplesPane = SampleRegistryPane(project.samples, controller)
        groupsPane = GroupRegistryPane(project.groups)
        scoreView = ScoreView(project.score, project.context)

        val flowGraphEditor = AudioFlowGraphPane(project.flowGraph, context)
        flowGraphEditor.setPrefSize(1000.0, 1000.0)
        flowGraphWindow = SubWindow(flowGraphEditor, "Audio flow graph", context)

        val globalControlsPane = GlobalControlsPane(project.globalControls, context)
        globalControlsWindow = SubWindow(globalControlsPane, "Global controls", context)
        globalControlsWindow.width = 500.0

        serverSetupCodeWindow = SubWindow(project.serverSetup.control, "ServerSetup", context)
        serverSetupCodeWindow.scene.registerShortcuts {
            on("Ctrl+S") {
                val setupCode = project.serverSetup.editor.result.now
                project.updateSetupCode(setupCode, SuperColliderObject.LiveCycleType.ServerBoot)
                notifyInfo("ServerSetup updated")
            }
        }
        serverSetupCodeWindow.resize(500.0, 500.0)
        serverTreeCodeWindow = SubWindow(project.serverTree.control, "ServerTree", context)
        serverTreeCodeWindow.scene.registerShortcuts {
            on("Ctrl+S") {
                val serverTreeCode = project.serverTree.editor.result.now
                project.updateSetupCode(serverTreeCode, SuperColliderObject.LiveCycleType.ServerTree)
                notifyInfo("ServerTree updated")
            }
        }
        serverTreeCodeWindow.resize(500.0, 500.0)

        player = ScorePlayer(scoreView, project, controller.client)
        shellWindow = SuperColliderShellController.createShellWindow(context)

        project.context[ScoreObjectSelector] = ScoreObjectSelector(project.context, scoreView)
        selectedObjectObserver = scoreView.selector.singleSelected.forEach { view ->
            if (view == null) {
                contextBar.children.clear()
            } else {
                contextBar.children.setAll(view.header)
            }
        }

        stage.scene.root = createLayout()
        stage.isResizable = true
        Platform.runLater {
            val screenSize = Screen.getPrimary().bounds
            stage.resize(screenSize.width * 0.75, screenSize.height)
        }
        runFXWithTimeout(1000) {
            scoreView.displayWholeScore()
        }
        displaysProject = true
    }

    override fun displayStartupScreen() {
        displaysProject = false
        val searchField = TextFields.createClearableTextField().apply {
            styleClass("search-field")
            promptText = "Search for project..."
        }
        val searchIcon = Icon.Find.getView()
        val btnOpen = button("Open") { controller.openProject() }
        val createNew = button("Create new") { controller.createNewProject() }
        val recentProjects = VBox().styleClass("recent-projects-list")
        for (proj in controller.recentProjects()) {
            val name = label(proj.nameWithoutExtension).styleClass("project-name")
            val path = label(proj.absolutePath).styleClass("project-path")
            val vertical = VBox(name, path)
            if (!proj.isFile) {
                path.textFill = Color.RED
            }
            val box = HBox(vertical).styleClass("project-box")
            vertical.setOnMouseClicked {
                if (proj.isDirectory) {
                    controller.openProject(proj)
                } else {
                    val remove =
                        showYesNoDialog("Project file does not exist. Remove from list?", default = true)
                    if (remove) {
                        controller.removeFromRecentProjects(proj)
                        recentProjects.children.remove(box)
                    }
                }
            }
            val removeBtn = Icon.Close.button(action = "Remove from list of recent projects") {
                val reallyRemove =
                    showYesNoDialog("Remove project from list of recent projects?", default = true)
                if (reallyRemove) {
                    controller.removeFromRecentProjects(proj)
                    recentProjects.children.remove(box)
                }
            }
            val space = infiniteSpace()
            box.children.addAll(space, removeBtn)
            box.alignment = Pos.CENTER_LEFT
            recentProjects.children.add(box)
        }
        val top = HBox(searchIcon, searchField, btnOpen, createNew).styleClass("startup-screen-top-bar")
        val layout = VBox(top, recentProjects).styleClass("startup-screen")
        stage.scene.root = layout
        stage.isMaximized = false
        stage.sizeToScene()
        stage.isResizable = false
    }

    private fun createLayout(): VBox {
        val toolPanes = SplitPane(synthDefsPane, busRegistryPane, samplesPane, groupsPane)
        toolPanes.orientation = Orientation.VERTICAL
        val horizontalSplitter = SplitPane(scoreView, toolPanes)
        SplitPane.setResizableWithParent(toolPanes, false)
        horizontalSplitter.sceneProperty().addListener { _ ->
            runFXWithTimeout(50) {
                horizontalSplitter.setDividerPositions(0.82)
            }
        }
        val toolbar = createToolbar()
        contextBar.prefWidthProperty().bind(toolbar.widthProperty().multiply(0.33))
        for (box in toolbar.children) HBox.setHgrow(box, Priority.ALWAYS)
        VBox.setVgrow(horizontalSplitter, Priority.ALWAYS)
        val layout = VBox(toolbar, horizontalSplitter)
        val context = project.context
        val commandLinePopup = CommandLinePopup(
            context, context[Properties.localCommandLine],
            arguments = createBundle { set(CommandLineControl.HISTORY_ITEMS, 0) }
        )
        layout.registerShortcuts {
            handleCommands(project, context, context[Properties.globalCommandLine])
            on("Alt+SPACE") {
                val focusedView = context[SelectionDistributor].focusedView.now
                if (focusedView is EditorControl<*>) {
                    val point = focusedView.localToScreen(0.0, 0.0)
                    commandLinePopup.show(stage, point.x, point.y)
                }
            }
            historyShortcuts(context[UndoManager])
        }
        return layout
    }

    private fun createToolbar(): HBox {
        val fileBar = createFileBar() styleClass "toolbar-part"
        val undoRedoBar = createUndoRedoBar() styleClass "toolbar-part"
        val playerBar = createPlayerBar() styleClass "toolbar-part"
        val interactionConfig = InteractionConfig(project.settings)
        val layoutBar = createLayoutBar() styleClass "toolbar-part"
        val miscBar = createMiscBar() styleClass "toolbar-part"
        return HBox(
            10.0,
            HBox(
                10.0,
                fileBar, undoRedoBar, playerBar, interactionConfig,
                toolSelector styleClass "toolbar-part", layoutBar
            ), HBox(
                contextBar styleClass "toolbar-part",
                miscBar
            )
        ).styleClass("toolbar")
    }

    private fun createUndoRedoBar(): HBox {
        val manager = project.context[UndoManager]
        val undo = Icon.Undo.button { manager.undo() }
        undo.tooltipProperty().bind(manager.undoText.map(::Tooltip).asObservableValue())
        undo.disableProperty().bind(manager.canUndo.not().asObservableValue())

        val redo = Icon.Redo.button { manager.redo() }
        redo.tooltipProperty().bind(manager.redoText.map(::Tooltip).asObservableValue())
        redo.disableProperty().bind(manager.canRedo.not().asObservableValue())

        return HBox(undo, redo)
    }

    private fun createMiscBar() = HBox(
        Icon.Console.button(action = "Open console") { shellWindow.show() },
        Icon.SetupCode.button(action = "Edit setup code") { ev ->
            if (ev.isShiftDown) serverSetupCodeWindow.show()
            else serverTreeCodeWindow.show()
        },
        Icon.Restart.button(action = "Restart server") { project.rebootServer() },
        Icon.Browser.button(action = "Open help browser") { project.context[HelpBrowser].show() },
        Icon.Graph.button(action = "Edit audio flow graph") { flowGraphWindow.show() },
        Icon.Settings.button(action = "Edit settings") { settingsWindow.show() },
        Icon.Knob.button(action = "Edit global controls") { globalControlsWindow.show() }
    )

    private fun createLayoutBar(): HBox {
        val horizontalBtn = Icon.Horizontal.button(action = "Create horizontal group") {
            project.context[ScoreObjectSelector].addLayoutGroup(LayoutAspect.Horizontal)
        }
        val verticalBtn = Icon.Vertical.button(action = "Create vertical group") {
            project.context[ScoreObjectSelector].addLayoutGroup(LayoutAspect.Vertical)
        }
        val removeHorizontalBtn = Icon.HorizontalRemove.button(action = "Remove from horizontal group") {
            project.context[ScoreObjectSelector].removeFromLayoutGroup(LayoutAspect.Horizontal)
        }
        val removeVerticalBtn = Icon.VerticalRemove.button(action = "Remove from vertical group") {
            project.context[ScoreObjectSelector].removeFromLayoutGroup(LayoutAspect.Vertical)
        }
        return HBox(horizontalBtn, verticalBtn, removeHorizontalBtn, removeVerticalBtn)
    }

    private fun createPlayerBar(): HBox {
        playBtn = Icon.Play.button(action = "Start playback") { _ -> togglePlay() }
        stopBtn = Icon.Stop.button(action = "Pause and free all nodes") { stop() }
        val goToStartBtn = Icon.GoToStart.button(action = "Move play head to start") { player.movePlayHead(0.0) }
        if (!controller.isSuperColliderReady) {
            playBtn.isDisable = true
            stopBtn.isDisable = true
        }
        return HBox(goToStartBtn, playBtn, stopBtn)
    }

    private fun togglePlay() {
        if (!player.isPlaying) {
            playBtn.graphic = Icon.Pause.getView()
            playBtn.tooltip = Tooltip("Pause playback")
            player.play()
        } else {
            playBtn.graphic = Icon.Play.getView()
            playBtn.tooltip = Tooltip("Start playback")
            player.pause()
        }
    }

    private fun stop() {
        player.pause()
        player.reset()
        playBtn.graphic = Icon.Play.getView()
        playBtn.tooltip = Tooltip("Start playback")
    }

    private fun createFileBar() = HBox(
        Icon.Save.button(action = "Save Project") { controller.saveProject() },
        Icon.Open.button(action = "Open Project") { controller.openProject() },
        Icon.Create.button(action = "Create new Project") { controller.createNewProject() },
        Icon.Export.button(action = "Export as SuperCollider script") { controller.exportAsScript() },
        Icon.Close.button(action = "Close project and open startup screen") { controller.closeProject() }
    )

    private fun Scene.addGlobalShortcuts() {
        registerShortcuts {
            if (!controller.isProjectOpened) return@registerShortcuts
            on("Ctrl+S") { controller.saveProject() }
            on("Ctrl+O") { controller.openProject() }
            on("Ctrl+N") { controller.createNewProject() }
            on("Ctrl+E") { controller.exportAsScript() }

            on("Ctrl+A") {
                context[ScoreObjectSelector].selectAll()
            }

            on("Ctrl?+SPACE") { togglePlay() }
            on("Ctrl?+PERIOD") { stop() }
            on("HOME") { scoreView.displayWholeScore() }
            on("DIGIT0") {
                scoreView.display(0.0, scoreView.displayedDuration)
                player.movePlayHead(0.0)
            }
            on("Shift+DIGIT0") {
                player.movePlayHead(scoreView.displayStart)
            }
            on("PAGE_UP") { scoreView.scroll(-100.0 / scoreView.pixelsPerSecond) }
            on("PAGE_DOWN") { scoreView.scroll(100.0 / scoreView.pixelsPerSecond) }

            on("ESCAPE") {
                scoreView.clearNewShape()
                scoreView.clearClipboard()
                context[ScoreObjectSelector].deselectAll()
                toolSelector.select(Tool.Pointer)
                toolSelector.getButton(Tool.Pointer).graphic = Tool.Pointer.icon.getView()
            }
            on("Alt?+DIGIT1") { toolSelector.select(Tool.Synth) }
            on("Alt?+DIGIT2") { toolSelector.select(Tool.Task) }
            on("Alt?+DIGIT3") { toolSelector.select(Tool.Envelope) }
            on("Alt?+DIGIT4") { toolSelector.select(Tool.Memo) }
            on("Alt?+DIGIT5") { toolSelector.select(Tool.PianoRoll) }
            on("Alt?+DIGIT6") { toolSelector.select(Tool.TempoGrid) }
            on("Alt?+DIGIT7") { toolSelector.select(Tool.Group) }
            on("Alt?+DIGIT8") { toolSelector.select(Tool.Cut) }
            on("Alt?+DIGIT9") { toolSelector.select(Tool.AddTime) }

            on("Alt?+G") { project.settings.displayTimeGrid.toggle() }
            on("Alt?+S") { project.settings.snapEnabled.toggle() }

            on("DELETE") { scoreView.removeSelected() }

            on("Ctrl+D") {
                val view = scoreView.selector.singleSelected.now
                if (view is SynthObjectView) {
                    view.openControlAssignment()
                }
            }
            on("Alt?+M") {
                scoreView.selector.toggleMuteSelected()
            }
            on("Alt?+L") {
                val view = scoreView.selector.singleSelected.now ?: return@on
                view.createLoop()
            }
            on("Alt?+D") {
                val selected = scoreView.selector.singleSelected.now ?: return@on
                val obj = selected.myObject.duplicateCopy()
                val view = selected.pane.getObjectView(obj)
                scoreView.selector.select(view, addToSelection = false)
            }
            on("Alt?+Shift+D") {
                val selected = scoreView.selector.singleSelected.now ?: return@on
                val obj = selected.myObject.duplicateClone()
                val view = selected.pane.getObjectView(obj)
                scoreView.selector.select(view, addToSelection = false)
            }
            on("Alt+SPACE") {
                val view = scoreView.selector.singleSelected.now ?: return@on
                view.playMyObject()
            }

            on("Ctrl+Alt+T") { controller.client.run("s.plotTree;") }
            on("F1") { context[HelpBrowser].show() }
            on("Ctrl+Shift+D") {
                showTextPrompt("Look up documentation", "", context) { searchText ->
                    context[HelpBrowser].searchDocumentation(searchText)
                    true
                }
            }
            on("Ctrl+T") { shellWindow.show() }
            on("Ctrl+Shift+F") { flowGraphWindow.show() }
            on("Ctrl+Alt+S") { settingsWindow.show() }
            on("Ctrl+G") { globalControlsWindow.show() }
            on("F5") { controller.restartScSynth() }

            on("Shift?+C") { ev ->
                toolSelector.select(Tool.Pointer)
                val view = context[ScoreObjectSelector].singleSelected.now ?: return@on
                var obj = view.myObject
                if (ev.isShiftDown && obj !is ClonedObject) obj = obj.clone()
                else if (!ev.isShiftDown && obj is ClonedObject) obj = obj.original
                scoreView.setClipboard(obj, view)
            }

            on("Ctrl+C") {
                toolSelector.select(Tool.Pointer)
                context[ScoreObjectSelector].copySelected()
            }
            on("Ctrl+Shift+C") {
                toolSelector.select(Tool.Pointer)
                context[ScoreObjectSelector].cloneSelected()
            }
        }
    }

    override fun readyToPlay() {
        if (displaysProject) {
            playBtn.isDisable = false
            stopBtn.isDisable = false
        }
    }

    override fun waitingForBoot() {
        if (displaysProject) {
            playBtn.isDisable = true
            stopBtn.isDisable = true
        }
    }

    companion object : PublicProperty<XenakisUI> by publicProperty("XenakisUI")
}