package xenakis.ui.launcher

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
import javafx.geometry.HorizontalDirection.RIGHT
import javafx.geometry.VerticalDirection
import javafx.scene.Scene
import javafx.scene.control.Label
import javafx.scene.control.SplitPane
import javafx.scene.input.KeyCode
import javafx.scene.input.KeyEvent
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.Region
import javafx.scene.layout.VBox
import javafx.stage.Screen
import reaktive.value.now
import reaktive.value.reactiveVariable
import xenakis.impl.copy
import xenakis.impl.unaryMinus
import xenakis.model.InteractionSettings.SnapOption
import xenakis.model.Logger
import xenakis.model.Settings
import xenakis.model.XenakisProject
import xenakis.model.obj.SuperColliderObject
import xenakis.model.player.PlaybackManager
import xenakis.model.registry.InstrumentRegistry
import xenakis.model.registry.ScoreObjectRegistry
import xenakis.model.score.ObjectPosition
import xenakis.model.score.Score
import xenakis.model.score.ScoreObjectGroup
import xenakis.model.score.ScoreObjectInstance
import xenakis.sc.client.SuperColliderClient
import xenakis.ui.actions.*
import xenakis.ui.actions.ToolSelector.Tool
import xenakis.ui.impl.*
import xenakis.ui.launcher.XenakisApp.Companion.primaryStage
import xenakis.ui.misc.*
import xenakis.ui.prompt.SimpleTextPrompt
import xenakis.ui.registry.*
import xenakis.ui.score.ScoreObjectSelectionManager
import xenakis.ui.score.ScoreObjectView
import xenakis.ui.score.ScoreView

class XenakisMainScreen(val project: XenakisProject) : Activity() {
    val toolSelector = ToolSelector(context)

    val instrumentsPane = InstrumentRegistryPane(project.instruments)
    val instrumentsWindow = SubWindow(instrumentsPane, "Instruments", context, SubWindow.Type.Undecorated)
    val processDefsPane = ProcessDefRegistryPane(project.processDefs)
    val processDefsWindow = SubWindow(processDefsPane, "Process Definitions", context, SubWindow.Type.Undecorated)
    private val busRegistryPane = BusRegistryPane(project.busses)
    val busesWindow = SubWindow(busRegistryPane, "Busses", context, SubWindow.Type.Undecorated)
    private val samplesPane = SampleRegistryPane(project.samples)
    val samplesWindow = SubWindow(samplesPane, "Samples", context, SubWindow.Type.Undecorated)
    private val groupsPane = GroupRegistryPane(project.groups)
    val groupsWindow = SubWindow(groupsPane, "Groups", context, SubWindow.Type.Undecorated)

    val logWindow = SubWindow(LogPane(context, Logger), "Log", context, SubWindow.Type.Undecorated)
    val settingsWindow = SubWindow(SettingsPane(context[Settings], context), "Settings", context)

    val flowGraphWindow: SubWindow
    val globalControlsWindow: SubWindow
    val serverTreeCodeWindow: SubWindow

    val serverSetupCodeWindow: SubWindow

    val scoreView: ScoreView
    private val detailPane = VBox(10.0).apply {
        styleClass("tool-pane")
        children.add(Label("Object details") styleClass "heading")
    }

    var playback: PlaybackManager
        private set

    val shellWindow = SuperColliderShellController.createShellWindow(context)

    override val context get() = project.context

    private val launcher get() = context[XenakisLauncher]

    val mode: Mode

    init {
        val largeScreenAvailable = Screen.getScreens().any { s -> s.bounds.width > 3000 }
        mode = if (largeScreenAvailable) Mode.Desktop else Mode.Laptop

        context[XenakisMainScreen] = this
        context[HelpBrowser] = HelpBrowser(context)
        settingsWindow.width = 1000.0
        settingsWindow.height = 1000.0

        scoreView = ScoreView(project.score, project.context)
        project.context[ScoreObjectSelectionManager] = ScoreObjectSelectionManager(project.context, scoreView)
        scoreView.initialize()

        val flowGraphEditor = AudioFlowGraphPane(project.flowGraph, context)
        flowGraphEditor.setPrefSize(1000.0, 1000.0)
        flowGraphWindow = SubWindow(flowGraphEditor, "Audio flow graph", context)

        val globalControlsPane = GlobalControlsPane(project.globalControls, context)
        globalControlsWindow = SubWindow(globalControlsPane, "Global controls", context)
        globalControlsWindow.width = 500.0

        val (serverSetup, serverTree) = project.setupCode
        serverSetupCodeWindow = SubWindow(serverSetup.control, "ServerSetup", context)
        serverSetupCodeWindow.scene.registerShortcuts {
            on("Ctrl+S") {
                val setupCode = serverSetup.editor.result.now
                project.updateSetupCode(setupCode, SuperColliderObject.LiveCycleType.ServerBoot)
                Logger.confirm("ServerSetup updated", Logger.Category.All)
            }
        }
        serverSetupCodeWindow.resize(500.0, 500.0)
        serverTreeCodeWindow = SubWindow(serverTree.control, "ServerTree", context)
        serverTreeCodeWindow.scene.registerShortcuts {
            on("Ctrl+S") {
                val serverTreeCode = serverTree.editor.result.now
                project.updateSetupCode(serverTreeCode, SuperColliderObject.LiveCycleType.ServerTree)
                Logger.confirm("ServerTree updated", Logger.Category.All)
            }
        }
        serverTreeCodeWindow.resize(500.0, 500.0)

        playback = PlaybackManager(scoreView)
        context[PlaybackManager] = playback
    }

    override fun beforeShowing() {
        stage.scene.addGlobalShortcuts()
        stage.scene.registerArrowKeys()
        stage.scene.initHextantScene(context)
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
            horizontalSplitter.sceneProperty().addListener { _, _, sc ->
                if (sc != null && sc.window != null) {
                    runFXWithTimeout(100) {
                        horizontalSplitter.setDividerPositions(0.82)
                    }
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
        val actions = Actions.singleObjectActions.withContext(context) + Actions.multiObjectActions.withContext(context)
        return ActionBar(actions)
    }

    private fun createToolbar(): HBox {
        return HBox(
            10.0,
            HBox(
                10.0,
                ActionBar(Actions.fileActions.withContext(launcher)),
                ActionBar(Actions.undoRedoActions.withContext(context[UndoManager])),
                ActionBar(Actions.playbackActions.withContext(playback)),
                InteractionConfig(project.settings),
                toolSelector styleClass "toolbar-part"
            ),
            infiniteSpace(),
            HBox(createContextBar()),
            infiniteSpace(),
            HBox(
                ActionBar(Actions.windowActions.withContext(this) + Actions.serverActions.withContext(project))
            ),
            infiniteSpace(),
            HBox(ActionBar(Actions.quitAction.withContext(launcher)))
        ).styleClass("toolbar")
    }

    private fun Scene.registerArrowKeys() {
        addEventFilter(KeyEvent.KEY_PRESSED) { ev ->
            if (ev.isTargetTextInput) return@addEventFilter
            if (ev.code in setOf(KeyCode.PAGE_UP, KeyCode.PAGE_DOWN)) {
                val delta = if (ev.code == KeyCode.PAGE_UP) 100.0 else -100.0
                scoreView.scroll(delta / scoreView.pixelsPerSecond)
            }
            if (ev.target !is ScoreObjectView) return@addEventFilter
            if (ev.code !in setOf(KeyCode.LEFT, KeyCode.RIGHT, KeyCode.UP, KeyCode.DOWN)) return@addEventFilter
            if (ev.isAltDown) {
                val view = context[ScoreObjectSelectionManager].focusedView.now ?: return@addEventFilter
                val inst = view.instance
                if (ev.code !in setOf(KeyCode.LEFT, KeyCode.RIGHT)) return@addEventFilter
                val start = if (ev.code == KeyCode.RIGHT) inst.start + inst.obj.duration
                else inst.start - inst.obj.duration
                val position = ObjectPosition(start, inst.y)
                val newInst = if (ev.isShiftDown) inst.clone(position) else inst.duplicate(position)
                inst.score!!.addObject(newInst)
                val newView = view.pane.getObjectView(newInst)
                runFXWithTimeout(10) {
                    context[ScoreObjectSelectionManager].select(newView, addToSelection = false)
                }
            } else {
                val selected = context[ScoreObjectSelectionManager].selectedViews
                    .associateBy { v -> v.instance }.values //filters out views that display the same instance
                val resize = ev.isControlDown
                val resizeType = ev.resizeType ?: return@addEventFilter
                for (view in selected) {
                    when (ev.code) {
                        KeyCode.LEFT -> view.adjustHorizontal(direction = HorizontalDirection.LEFT, resize, resizeType)
                        KeyCode.RIGHT -> view.adjustHorizontal(direction = RIGHT, resize, resizeType)
                        KeyCode.UP -> view.adjustVertical(direction = VerticalDirection.UP, resize, resizeType)
                        KeyCode.DOWN -> view.adjustVertical(direction = VerticalDirection.DOWN, resize, resizeType)
                        else -> throw AssertionError()
                    }
                }
            }
            ev.consume()
        }
    }

    private fun Scene.addGlobalShortcuts() {
        registerShortcuts {
            registerActions(Actions.fileActions.withContext(launcher))
            registerActions(Actions.quitAction.withContext(launcher))
            registerActions(Actions.playbackActions.withContext(playback))
            registerActions(Actions.scoreNavigationActions.withContext(scoreView))
            addToolSelectionShortcuts()
            addGridRelatedShortcuts()
            registerActions(Actions.windowActions.withContext(this@XenakisMainScreen))
            addToolWindowShortcuts()
            val objectCtx = ObjectActionContext.MultiObjectContext(context[ScoreObjectSelectionManager])
            registerActions(Actions.multiObjectActions.withContext(objectCtx))
            addSelectionRelatedShortcuts()
            registerActions(Actions.serverActions.withContext(project))
        }
    }

    private fun KeyEventHandlerBody<Unit>.addSelectionRelatedShortcuts() {
        on("ESCAPE") {
            scoreView.endNewObject()
            scoreView.clearClipboard()
            if (!playback.player.isPlaying.now && playback.playHead.pane is ScoreObjectView) {
                val attachedView = playback.playHead.pane as ScoreObjectView
                val absoluteTime = attachedView.absolutePosition.time + playback.playHead.currentTime
                playback.attachToMainScore()
                playback.playHead.movePlayHead(absoluteTime)
            }
            context[ScoreObjectSelectionManager].deselectAll()
            context[XenakisMainScreen].scoreView.requestFocus()
            context[XenakisMainScreen].toolSelector.select(Tool.Pointer)
        }
        on("Ctrl+A") { ev ->
            if (ev.isTargetTextInput) return@on
            context[ScoreObjectSelectionManager].selectAll()
        }
        on("Ctrl+Shift+A") {
            val selected = scoreView.selector.focusedView.now ?: return@on
            for (inst in selected.pane.score.instancesOf(selected.instance.obj)) {
                if (inst != selected.instance) {
                    val view = selected.pane.getObjectView(inst)
                    context[ScoreObjectSelectionManager].select(view, addToSelection = true)
                }
            }
        }
        on("C") { ev ->
            if (ev.isTargetTextInput) return@on
            val selected = scoreView.selector.focusedView.now ?: return@on
            setClipboard(selected)
        }
        on("Ctrl+C") { ev ->
            if (ev.isTargetTextInput) return@on
            val selected = scoreView.selector.selectedInstances.toList()
            scoreView.selector.setSystemClipboard(selected)
        }
        on("X") { ev ->
            if (ev.isTargetTextInput) return@on
            toolSelector.select(Tool.Pointer)
            val view = context[ScoreObjectSelectionManager].focusedView.now ?: return@on
            val inst = view.instance
            inst.score?.removeObject(inst)
            scoreView.setClipboard(inst.obj, view)
        }
        on("U") { ev ->
            if (ev.isTargetTextInput) return@on
            context.compoundEdit("Unlink object from its original") {
                for ((obj, instances) in scoreView.selector.selectedInstances.groupBy { inst -> inst.obj }) {
                    val name = context[ScoreObjectRegistry].nameForClone(obj)
                    val clone = obj.clone(name)
                    for (oldInst in instances) {
                        val newInst = ScoreObjectInstance(clone, oldInst.position, oldInst.muted.copy())
                        oldInst.score?.addObject(newInst)
                        oldInst.score?.removeObject(oldInst)
                    }
                }
            }
        }

        on("Shift?+G") { ev ->
            if (ev.isTargetTextInput) return@on
            val views = scoreView.selector.selectedViews
            //import to get a single ScorePane (not a single Score)
            // because we want the instances to be from one ScorePane (or the root score)
            val parentPane = views.mapTo(mutableSetOf()) { v -> v.pane }.singleOrNull() ?: return@on
            val instances = views.map { v -> v.instance }
            val minT = instances.minOf { inst -> inst.start }
            val minY = instances.minOf { inst -> inst.y }
            val maxT = instances.maxOf { inst -> inst.start + inst.duration }
            val maxY = instances.maxOf { inst -> inst.y + inst.height }
            val relativePosition = ObjectPosition(-minT, -minY)
            val recurse = ev.isShiftDown
            val newScore = Score()
            val name = context[ScoreObjectRegistry].availableName("group")
            val newObj = ScoreObjectGroup(reactiveVariable(name), newScore)
            newObj.setInitialSize(maxT - minT, maxY - minY)
            val newInst = ScoreObjectInstance(newObj, minT, minY)
            parentPane.score.addObject(newInst)
            context.compoundEdit("Create group from objects") {
                for (inst in instances) {
                    inst.moveInto(newScore, relativePosition, recurse)
                }
            }
            runFXWithTimeout {
                val view = parentPane.getObjectView(newInst)
                context[ScoreObjectSelectionManager].select(view, addToSelection = false)
            }
        }
    }

    private fun KeyEventHandlerBody<Unit>.addToolWindowShortcuts() {
        on("Ctrl+I") { instrumentsWindow.show() }
        on("Ctrl+P") { processDefsWindow.show() }

        on("Shift+I") { selectInstrument() }

        on("Ctrl+Shift+D") {
            val searchText = SimpleTextPrompt("Look up documentation", "").showDialog(context) ?: return@on
            context[HelpBrowser].searchDocumentation(searchText)
        }
    }

    private fun selectInstrument() {
        val registry = context[InstrumentRegistry]
        SimpleSearchableRegistryView(registry, "Select instrument")
            .showPopup(context, initialOption = registry.selectedInstrument.now) { instr ->
                registry.select(instr)
            }
    }

    private fun KeyEventHandlerBody<Unit>.addToolSelectionShortcuts() {
        for (tool in Tool.entries.take(10)) {
            on("DIGIT${tool.ordinal}") { ev ->
                if (!ev.isTargetTextInput) {
                    toolSelector.select(tool)
                }
            }
            if (tool != Tool.Pointer) {
                on("Ctrl+DIGIT${tool.ordinal}") {
                    toolSelector.select(tool)
                }
            }
        }
        on("Ctrl+Shift+P") {
            toolSelector.select(Tool.Pointer)
        }
    }

    private fun KeyEventHandlerBody<Unit>.addGridRelatedShortcuts() {
        on("Alt+S") { project.settings.snapOption.now = SnapOption.Seconds }
        on("Alt+B") { project.settings.snapOption.now = SnapOption.Bars }
        on("Alt+N") { project.settings.snapOption.now = SnapOption.Beats }
        on("Alt+T") { project.settings.snapOption.now = SnapOption.Ticks }
    }

    private fun setClipboard(selected: ScoreObjectView) {
        toolSelector.select(Tool.Pointer)
        scoreView.setClipboard(selected.instance.obj, selected)
    }

    enum class Mode {
        Desktop, Laptop;
    }

    override fun close() {
        context[SuperColliderClient].quit()
        //TODO is there more cleanup to do?
    }

    companion object : PublicProperty<XenakisMainScreen> by publicProperty("XenakisUI")
}