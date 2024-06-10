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
import hextant.fx.handleCommands
import hextant.fx.initHextantScene
import hextant.fx.label
import hextant.fx.registerShortcuts
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
import javafx.stage.Stage
import org.controlsfx.control.textfield.TextFields
import reaktive.Observer
import reaktive.value.binding.map
import reaktive.value.binding.not
import reaktive.value.forEach
import reaktive.value.fx.asObservableValue
import reaktive.value.now
import reaktive.value.toggle
import xenakis.model.LayoutManager.LayoutAspect
import xenakis.model.Settings
import xenakis.model.XenakisProject
import xenakis.ui.ToolSelector.Tool

class XenakisUI(private val stage: Stage, private val controller: XenakisController) : XenakisListener {
    val project get() = controller.currentProject

    val toolSelector = ToolSelector()

    private lateinit var serverSetupCodePane: CodePane
    private lateinit var beforePlayCodePane: CodePane
    private lateinit var synthDefsPane: InstrumentRegistryPane
    private lateinit var busRegistryPane: BusRegistryPane
    private lateinit var buffersPane: BufferRegistryPane
    private lateinit var groupsPane: GroupRegistryPane
    private lateinit var scoreView: ScoreView
    private lateinit var flowGraphWindow: SubWindow
    private lateinit var globalControlsWindow: SubWindow
    private val settingsWindow: Stage

    private lateinit var playBtn: Button
    private lateinit var stopBtn: Button
    private lateinit var recordBtn: Button

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
        stage.scene.initHextantScene(context)
    }

    override fun displayProject(project: XenakisProject) {
        serverSetupCodePane = CodePane("Server setup", project.serverSetup.control)
        beforePlayCodePane = CodePane("Play setup", project.beforePlay.control)
        synthDefsPane = InstrumentRegistryPane(project.instruments)
        context[InstrumentRegistryPane] = synthDefsPane
        busRegistryPane = BusRegistryPane(project.busses)
        buffersPane = BufferRegistryPane(project.buffers, project, controller)
        groupsPane = GroupRegistryPane(project.groups)
        scoreView = ScoreView(project.score, project.context)

        val flowGraphEditor = AudioFlowGraphPane(project.flowGraph, context)
        flowGraphEditor.setPrefSize(1000.0, 1000.0)
        flowGraphWindow = SubWindow(flowGraphEditor, "Audio flow graph", context)

        val globalControlsPane = GlobalControlsPane(project.globalControls, context)
        globalControlsWindow = SubWindow(globalControlsPane, "Global controls", context)
        globalControlsWindow.width = 500.0

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
            stage.isMaximized = true
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
                if (proj.isFile) {
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
        val leftSplitter = SplitPane(synthDefsPane, busRegistryPane, buffersPane, groupsPane)
        val rightSplitter = SplitPane(serverSetupCodePane, beforePlayCodePane)
        leftSplitter.minWidth = 400.0
        rightSplitter.minWidth = 400.0
        leftSplitter.orientation = Orientation.VERTICAL
        rightSplitter.orientation = Orientation.VERTICAL
        val horizontalSplitter = SplitPane(leftSplitter, scoreView, rightSplitter)
        SplitPane.setResizableWithParent(leftSplitter, false)
        SplitPane.setResizableWithParent(rightSplitter, false)
        val toolbar = createToolbar()
        contextBar.prefWidthProperty().bind(toolbar.widthProperty().multiply(0.33))
        for (box in toolbar.children) HBox.setHgrow(box, Priority.ALWAYS)
        VBox.setVgrow(horizontalSplitter, Priority.ALWAYS)
        val layout = VBox(toolbar, horizontalSplitter)
        addShortcuts(layout)
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
        Icon.Restart.button(action = "Restart server") { project.rebootServer() },
        Icon.Browser.button(action = "Open help browser") { project.context[HelpBrowser].show() },
        Icon.Graph.button(action = "Edit audio flow graph") { flowGraphWindow.show() },
        Icon.Settings.button(action = "Edit settings") { settingsWindow.show() },
        Icon.Knob.button(action = "Open global controls") { globalControlsWindow.show() }
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
        recordBtn = Icon.RecordInactive.button(action = "Start recording") { toggleRecord() }
        if (!controller.isSuperColliderReady) {
            playBtn.isDisable = true
            stopBtn.isDisable = true
            recordBtn.isDisable = true
        }
        return HBox(playBtn, stopBtn /*recordBtn*/)
    }

    private fun toggleRecord() {
        player.toggleRecording()
        if (player.isRecording) {
            recordBtn.graphic = Icon.RecordActive.getView()
            recordBtn.tooltip = Tooltip("Finish recording")
        } else {
            recordBtn.graphic = Icon.RecordInactive.getView()
            recordBtn.tooltip = Tooltip("Start recording")
        }
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

    private fun addShortcuts(layout: VBox) {
        layout.registerShortcuts {
            on("Ctrl+S") { controller.saveProject() }
            on("Ctrl+O") { controller.openProject() }
            on("Ctrl+N") { controller.createNewProject() }
            on("Ctrl+E") { controller.exportAsScript() }

            on("Ctrl+A") {
                context[ScoreObjectSelector].selectAll()
            }

            on("Ctrl+SPACE") { togglePlay() }
            on("Ctrl+PERIOD") { stop() }

            on("ESCAPE") {
                scoreView.clearNewShape()
                context[ScoreObjectSelector].deselectAll()
                toolSelector.select(Tool.Pointer)
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
                selected.myObject.duplicateCopy()
            }
            on("Alt?+Shift+D") {
                val selected = scoreView.selector.singleSelected.now ?: return@on
                selected.myObject.duplicateClone()
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

            on("Ctrl?+C") {
                scoreView.selector.copySelected()
            }
            on("Ctrl?+Shift+C") {
                scoreView.selector.cloneSelected()
            }
        }
    }

    override fun readyToPlay() {
        if (displaysProject) {
            playBtn.isDisable = false
            stopBtn.isDisable = false
            recordBtn.isDisable = false
        }
    }

    override fun waitingForBoot() {
        if (displaysProject) {
            playBtn.isDisable = true
            stopBtn.isDisable = true
            recordBtn.isDisable = true
        }
    }

    companion object : PublicProperty<XenakisUI> by publicProperty("XenakisUI")
}