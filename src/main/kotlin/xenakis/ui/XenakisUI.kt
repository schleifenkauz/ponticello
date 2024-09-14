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
import hextant.undo.compoundEdit
import hextant.undo.historyShortcuts
import javafx.application.Platform
import javafx.geometry.HorizontalDirection
import javafx.geometry.Orientation
import javafx.geometry.Pos
import javafx.geometry.VerticalDirection
import javafx.scene.Scene
import javafx.scene.control.*
import javafx.scene.input.KeyCode
import javafx.scene.input.KeyEvent
import javafx.scene.layout.*
import javafx.scene.paint.Color
import javafx.stage.Screen
import javafx.stage.Stage
import org.controlsfx.control.textfield.TextFields
import reaktive.Observer
import reaktive.value.binding.equalTo
import reaktive.value.binding.map
import reaktive.value.binding.not
import reaktive.value.forEach
import reaktive.value.fx.asObservableValue
import reaktive.value.now
import reaktive.value.toggle
import xenakis.model.*
import xenakis.ui.ScoreView.ClipboardMode
import xenakis.ui.ToolSelector.Tool

class XenakisUI(
    private val stage: Stage,
    private val controller: XenakisController,
    private val mode: Mode
) : XenakisListener {
    private val project get() = controller.currentProject

    val toolSelector = ToolSelector()

    private val progressBar = ProgressBar()
    private val statusText = Label()

    private lateinit var instrumentsPane: InstrumentRegistryPane
    private lateinit var instrumentsWindow: SubWindow
    private lateinit var busRegistryPane: BusRegistryPane
    private lateinit var busesWindow: SubWindow
    private lateinit var samplesPane: SampleRegistryPane
    private lateinit var samplesWindow: SubWindow
    private lateinit var groupsPane: GroupRegistryPane
    private lateinit var groupsWindow: SubWindow
    lateinit var scoreView: ScoreView
        private set
    private lateinit var flowGraphWindow: SubWindow
    private lateinit var globalControlsWindow: SubWindow
    private lateinit var serverTreeCodeWindow: SubWindow
    private lateinit var serverSetupCodeWindow: SubWindow
    private val settingsWindow: Stage
    private val contextBar = HBox()
    private val detailPane = VBox(10.0).apply {
        styleClass("tool-pane")
        children.add(Label("Object details") styleClass "tool-pane-heading")
    }

    private lateinit var playBtn: Button
    private lateinit var stopBtn: Button
    lateinit var player: ScorePlayer

    private lateinit var shellWindow: Stage
    private lateinit var selectedObjectObserver: Observer

    private var displaysProject = false

    private val context get() = controller.context

    init {
        context[XenakisUI] = this
        context[HelpBrowser] = HelpBrowser(context)
        settingsWindow = SubWindow(SettingsPane(context[Settings], context), "Settings", context)
        settingsWindow.width = 1000.0
        settingsWindow.height = 1000.0
        stage.scene = Scene(Pane())
        stage.scene.addGlobalShortcuts()
        stage.scene.registerArrowKeys()
        stage.scene.initHextantScene(context)
    }

    override fun displayProject(project: XenakisProject) {
        instrumentsPane = InstrumentRegistryPane(project.instruments)
        context[InstrumentRegistryPane] = instrumentsPane
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

        project.context[ScoreObjectSelectionManager] = ScoreObjectSelectionManager(project.context, scoreView)
        selectedObjectObserver = scoreView.selector.singleSelected.forEach { view ->
            if (detailPane.children.size == 2) detailPane.children.removeAt(1)
            if (view == null) {
                contextBar.isVisible = false
            } else {
                detailPane.children.add(view.getDetailPane())
                contextBar.isVisible = true
                contextBar.children.setAll(view.actions)
            }
        }

        stage.scene.root = createLayout()
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
        runFXWithTimeout(1000) {
            scoreView.displayWholeScore()
        }
        displaysProject = true
    }

    override fun displayLoadScreen() {
        val logo = Icon.AppIcon.getView(size = 500.0)
        progressBar.prefWidth = logo.prefWidth(-1.0)
        stage.scene.root = VBox(logo, StackPane(progressBar, statusText))
        stage.sizeToScene()
        Platform.runLater {
            stage.centerOnScreen()
        }
    }

    override fun displayProgress(progress: Double, status: String) {
        progressBar.progress = progress
        statusText.text = status
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
                    val remove = showYesNoDialog("Project file does not exist. Remove from list?", default = true)
                    if (remove == true) {
                        controller.removeFromRecentProjects(proj)
                        recentProjects.children.remove(box)
                    }
                }
            }
            val removeBtn = Icon.Close.button(action = "Remove from list of recent projects") {
                val reallyRemove = showYesNoDialog("Remove project from list of recent projects?", default = true)
                if (reallyRemove == true) {
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
        stage.centerOnScreen()
        stage.isResizable = false
    }

    private fun createLayout(): VBox {
        var mainView: Region = scoreView
        if (mode == Mode.Desktop) {
            val toolPanes = SplitPane(detailPane, instrumentsPane, busRegistryPane, samplesPane)
            toolPanes.orientation = Orientation.VERTICAL
            val horizontalSplitter = SplitPane(scoreView, toolPanes)
            horizontalSplitter.sceneProperty().addListener { _ ->
                runFXWithTimeout(50) {
                    horizontalSplitter.setDividerPositions(0.82)
                }
            }
            mainView = horizontalSplitter
        }
        val toolbar = createToolbar()
        for (box in toolbar.children) HBox.setHgrow(box, Priority.ALWAYS)
        VBox.setVgrow(mainView, Priority.ALWAYS)
        val layout = VBox(toolbar, mainView)
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
        val miscBar = createMiscBar() styleClass "toolbar-part"
        return HBox(
            10.0,
            HBox(
                10.0,
                fileBar, undoRedoBar, playerBar, interactionConfig,
                toolSelector styleClass "toolbar-part"
            ),
            infiniteSpace(),
            HBox(contextBar styleClass "toolbar-part"),
            infiniteSpace(),
            miscBar
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

    private fun createMiscBar() = hbox {
        children {
            +Icon.Console.button(action = "Open console") { shellWindow.show() }
            +Icon.SetupCode.button(action = "Edit setup code") { ev ->
                if (ev.isShiftDown) serverSetupCodeWindow.show()
                else serverTreeCodeWindow.show()
            }
            +Icon.Restart.button(action = "Restart server") { controller.restartScSynth() }
            +Icon.Browser.button(action = "Open help browser") { project.context[HelpBrowser].show() }
            +Icon.Graph.button(action = "Edit audio flow graph") { flowGraphWindow.show() }
            +Icon.Settings.button(action = "Edit settings") { settingsWindow.show() }
            +Icon.Knob.button(action = "Edit global controls") { globalControlsWindow.show() }
            if (mode == Mode.Laptop) {
                busesWindow = SubWindow(busRegistryPane, "Busses", context, type = SubWindow.Type.Popup)
                samplesWindow = SubWindow(samplesPane, "Samples", context, type = SubWindow.Type.Popup)
                instrumentsWindow = SubWindow(instrumentsPane, "Instruments", context, type = SubWindow.Type.Popup)
                groupsWindow = SubWindow(groupsPane, "Groups", context, type = SubWindow.Type.Popup)
                +Icon.Bus.button(action = "Show buses") { busesWindow.show() }
                +Icon.Samples.button(action = "Show samples") { samplesWindow.show() }
                +Icon.Instrument.button(action = "Show instruments") { instrumentsWindow.show() }
                add(Icon.Details.button(action = "Edit object properties") { showDetailPaneOfSelectedObject() }) {
                    disableProperty().bind(scoreView.selector.singleSelected.equalTo(null).asObservableValue())
                }
            }
        }
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
        Icon.Close.button(action = "Close project and open startup screen") { controller.closeProject() }
    )

    private fun Scene.registerArrowKeys() {
        addEventFilter(KeyEvent.ANY) { ev ->
            if (!ev.isAltDown && ev.isTargetTextInput) return@addEventFilter
            if (ev.code !in setOf(KeyCode.LEFT, KeyCode.RIGHT, KeyCode.UP, KeyCode.DOWN)) return@addEventFilter
            val stretch = ev.isShiftDown
            val resize = ev.isControlDown
            if (stretch && !resize) return@addEventFilter
            ev.consume()
            if (ev.eventType != KeyEvent.KEY_PRESSED) return@addEventFilter
            val selected = context[ScoreObjectSelectionManager].selectedViews
            for (view in selected) {
                when (ev.code) {
                    KeyCode.LEFT -> view.adjustHorizontal(direction = HorizontalDirection.LEFT, resize, stretch)
                    KeyCode.RIGHT -> view.adjustHorizontal(direction = HorizontalDirection.RIGHT, resize, stretch)
                    KeyCode.UP -> view.adjustVertical(direction = VerticalDirection.UP, resize, stretch)
                    KeyCode.DOWN -> view.adjustVertical(direction = VerticalDirection.DOWN, resize, stretch)
                    else -> throw AssertionError()
                }
            }
        }
    }

    private fun Scene.addGlobalShortcuts() {
        registerShortcuts {
            if (!controller.isProjectOpened) return@registerShortcuts
            on("Ctrl+S") { controller.saveProject() }
            on("Ctrl+O") { controller.openProject() }
            on("Ctrl+N") { controller.createNewProject() }

            on("Ctrl+A") {
                context[ScoreObjectSelectionManager].selectAll()
            }

            on("Ctrl?+SPACE") { ev ->
                if (ev.isControlDown || !ev.isTargetTextInput) togglePlay()
            }
            on("Ctrl?+PERIOD") { ev ->
                if (ev.isControlDown || !ev.isTargetTextInput) stop()
            }
            on("HOME") { scoreView.displayWholeScore() }
            on("DIGIT0") { ev ->
                if (!ev.isTargetTextInput) {
                    scoreView.display(0.0, scoreView.displayedDuration)
                    player.movePlayHead(0.0)
                }
            }
            on("Shift+DIGIT0") { ev ->
                if (!ev.isTargetTextInput) {
                    player.movePlayHead(scoreView.displayStart)
                }
            }
            on("PAGE_UP") { ev ->
                if (!ev.isTargetTextInput) {
                    scoreView.scroll(-100.0 / scoreView.pixelsPerSecond)
                }
            }
            on("PAGE_DOWN") { ev ->
                if (!ev.isTargetTextInput) {
                    scoreView.scroll(100.0 / scoreView.pixelsPerSecond)
                }
            }

            on("ESCAPE") {
                scoreView.clearNewShape()
                scoreView.clearClipboard()
                context[ScoreObjectSelectionManager].deselectAll()
                toolSelector.select(Tool.Pointer)
                toolSelector.getButton(Tool.Pointer).graphic = Tool.Pointer.icon.getView()
            }
            registerToolNumber(Tool.Synth, 1)
            registerToolNumber(Tool.Task, 2)
            registerToolNumber(Tool.Envelope, 3)
            registerToolNumber(Tool.Memo, 4)
            registerToolNumber(Tool.PianoRoll, 5)
            registerToolNumber(Tool.TempoGrid, 6)
            registerToolNumber(Tool.Group, 7)
            registerToolNumber(Tool.Cut, 8)
            registerToolNumber(Tool.AddTime, 9)

            on("Alt+T") { project.settings.displayTimeGrid.toggle() }
            on("Alt+S") { project.settings.snapEnabled.toggle() }

            if (mode == Mode.Laptop) {
                on("Alt+G") { ev ->
                    if (ev.isAltDown || !ev.isTargetTextInput) {
                        groupsWindow.show()
                    }
                }
                on("Alt?+B") { ev ->
                    if (ev.isAltDown || !ev.isTargetTextInput) {
                        busesWindow.show()
                    }
                }
                on("Alt?+F") { ev ->
                    if (ev.isAltDown || !ev.isTargetTextInput) {
                        samplesWindow.show()
                    }
                }
                on("Alt?+I") { ev ->
                    if (ev.isAltDown || !ev.isTargetTextInput) {
                        instrumentsWindow.show()
                    }
                }
                on("Alt?+P") { ev ->
                    if (ev.isAltDown || !ev.isTargetTextInput) {
                        showDetailPaneOfSelectedObject()
                    }
                }
            }

            on("DELETE") { ev ->
                if (!ev.isTargetTextInput) {
                    scoreView.removeSelected()
                }
            }

            on("Alt?+M") { ev ->
                if (ev.isAltDown || !ev.isTargetTextInput) {
                    scoreView.selector.toggleMuteSelected()
                }
            }
            on("Alt?+L") { ev ->
                if (ev.isAltDown || !ev.isTargetTextInput) {
                    val view = scoreView.selector.singleSelected.now ?: return@on
                    view.createLoop()
                }
            }
            on("Alt?+R") { ev ->
                if (ev.isAltDown || !ev.isTargetTextInput) {
                    for (inst in scoreView.selector.selectedInstances) {
                        val obj = inst.obj
                        if (obj is SynthObject) {
                            obj.reverse()
                        }
                    }
                }
            }
            on("Alt?+Shift?+D") { ev ->
                if (ev.isAltDown || !ev.isTargetTextInput) {
                    val selected = scoreView.selector.singleSelected.now ?: return@on
                    val inst = selected.instance
                    val obj =
                        if (ev.isShiftDown) inst.duplicate(inst.time + inst.duration, inst.y)
                        else {
                            val name = context[ScoreObjectRegistry].nameForClone(inst.obj)
                            inst.clone(name, inst.time + inst.duration, inst.y)
                        }
                    inst.score.addObject(obj)
                    val view = selected.pane.getObjectView(obj)
                    scoreView.selector.select(view, addToSelection = false)
                }
            }

            on("Alt?+U") { ev ->
                if (ev.isAltDown || !ev.isTargetTextInput) {
                    val inst = scoreView.selector.singleSelected.now?.instance ?: return@on
                    context.compoundEdit("Unlink object from its original") {
                        val name = context[ScoreObjectRegistry].nameForClone(inst.obj)
                        val clone = inst.clone(name, inst.time, inst.y)
                        inst.score.removeObject(inst)
                        inst.score.addObject(clone)
                    }
                    //TODO multiple instances of the same object should be linked afterwards
                }
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
                if (!ev.isTargetTextInput) {
                    toolSelector.select(Tool.Pointer)
                    val view = context[ScoreObjectSelectionManager].singleSelected.now ?: return@on
                    val obj = view.instance.obj
                    val mode = if (ev.isShiftDown) ClipboardMode.Duplicate else ClipboardMode.Clone
                    scoreView.setClipboard(obj, view, mode)
                }
            }

            on("Ctrl+C") {
                toolSelector.select(Tool.Pointer)
                context[ScoreObjectSelectionManager].cloneToClipboard()
            }
            on("Ctrl+Shift+C") {
                toolSelector.select(Tool.Pointer)
                context[ScoreObjectSelectionManager].duplicateToClipboard()
            }
        }
    }

    private fun showDetailPaneOfSelectedObject() {
        val selected = scoreView.selector.singleSelected.now ?: return
        val pane = selected.getDetailPane()
        val name = selected.instance.obj.name.now
        val window = SubWindow(pane, "Configure $name", context, type = SubWindow.Type.Popup)
        window.show()
    }

    private fun KeyEventHandlerBody<Unit>.registerToolNumber(tool: Tool, digit: Int) {
        on("Alt?+DIGIT$digit") { ev ->
            if (ev.isAltDown || !ev.isTargetTextInput) {
                toolSelector.select(tool)
            }
        }
    }

    private val KeyEvent.isTargetTextInput get() = target is TextInputControl || target is Spinner<*>

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

    enum class Mode {
        Desktop, Laptop;
    }

    companion object : PublicProperty<XenakisUI> by publicProperty("XenakisUI")
}