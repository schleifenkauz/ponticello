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
import javafx.geometry.HorizontalDirection.RIGHT
import javafx.geometry.VerticalDirection
import javafx.scene.Scene
import javafx.scene.control.*
import javafx.scene.input.KeyCode
import javafx.scene.input.KeyEvent
import javafx.scene.layout.*
import javafx.stage.Screen
import javafx.stage.Stage
import reaktive.Observer
import reaktive.value.binding.equalTo
import reaktive.value.binding.map
import reaktive.value.binding.not
import reaktive.value.forEach
import reaktive.value.fx.asObservableValue
import reaktive.value.now
import reaktive.value.reactiveVariable
import reaktive.value.toggle
import xenakis.impl.asTime
import xenakis.impl.times
import xenakis.impl.unaryMinus
import xenakis.impl.zero
import xenakis.model.InteractionSettings.SnapOption
import xenakis.model.Logger
import xenakis.model.Settings
import xenakis.model.XenakisProject
import xenakis.model.obj.BusObject
import xenakis.model.obj.SuperColliderObject
import xenakis.model.player.PlaybackManager
import xenakis.model.registry.BusRegistry
import xenakis.model.registry.InstrumentRegistry
import xenakis.model.registry.ScoreObjectRegistry
import xenakis.model.score.*
import xenakis.sc.Rate
import xenakis.sc.client.SuperColliderClient
import xenakis.ui.ToolSelector.Tool
import xenakis.ui.XenakisApp.Companion.primaryStage
import xenakis.ui.impl.*
import xenakis.ui.launcher.Activity
import xenakis.ui.launcher.XenakisLauncher
import xenakis.ui.misc.*
import xenakis.ui.prompt.IntegerPrompt
import xenakis.ui.prompt.NamePrompt
import xenakis.ui.prompt.SimpleTextPrompt
import xenakis.ui.registry.*
import xenakis.ui.score.ScoreObjectSelectionManager
import xenakis.ui.score.ScoreObjectView
import xenakis.ui.score.ScoreView

class XenakisMainScreen(val project: XenakisProject) : Activity() {
    val toolSelector = ToolSelector(context)

    val instrumentsPane = InstrumentRegistryPane(project.instruments)
    val instrumentsWindow = SubWindow(instrumentsPane, "Instruments", context, SubWindow.Type.Undecorated)
    var processDefsPane = ProcessDefRegistryPane(project.processDefs)
    var processDefsWindow = SubWindow(processDefsPane, "Process Definitions", context, SubWindow.Type.Undecorated)
    private val busRegistryPane = BusRegistryPane(project.busses)
    private val busesWindow = SubWindow(busRegistryPane, "Busses", context, SubWindow.Type.Undecorated)
    private val samplesPane = SampleRegistryPane(project.samples)
    private val samplesWindow = SubWindow(samplesPane, "Samples", context, SubWindow.Type.Undecorated)
    private val groupsPane = GroupRegistryPane(project.groups)
    private val groupsWindow = SubWindow(groupsPane, "Groups", context, SubWindow.Type.Undecorated)

    private val logWindow = SubWindow(LogPane(context, Logger), "Log", context, SubWindow.Type.Undecorated)
    private val settingsWindow = SubWindow(SettingsPane(context[Settings], context), "Settings", context)

    private val flowGraphWindow: SubWindow
    private val globalControlsWindow: SubWindow
    private val serverTreeCodeWindow: SubWindow

    private val serverSetupCodeWindow: SubWindow

    val scoreView: ScoreView
    private val contextBar = HBox()
    private val detailPane = VBox(10.0).apply {
        styleClass("tool-pane")
        children.add(Label("Object details") styleClass "heading")
    }

    private lateinit var playBtn: Button
    private lateinit var stopBtn: Button
    private lateinit var recordingBtn: Button
    var playback: PlaybackManager
        private set

    private var shellWindow: Stage
    private var selectedObjectObserver: Observer

    override val context get() = project.context

    private val launcher get() = context[XenakisLauncher]

    private val mode: Mode

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
        shellWindow = SuperColliderShellController.createShellWindow(context)

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

    private fun createToolbar(): HBox {
        val fileBar = createFileBar() styleClass "toolbar-part"
        val undoRedoBar = createUndoRedoBar() styleClass "toolbar-part"
        val playerBar = createPlayerBar() styleClass "toolbar-part"
        val interactionConfig = InteractionConfig(project.settings)
        val miscBar = createMiscBar() styleClass "toolbar-part"
        return HBox(
            10.0,
            HBox(
                10.0, fileBar, undoRedoBar, playerBar, interactionConfig, toolSelector styleClass "toolbar-part"
            ),
            infiniteSpace(),
            HBox(contextBar styleClass "toolbar-part"),
            infiniteSpace(),
            miscBar.also { HBox.setHgrow(it, Priority.NEVER) }).styleClass("toolbar")
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
            +Icon.Log.button(action = "Show log (Ctrl+L)") { ev ->
                if (ev.isShiftDown) {
                    SimpleSearchableListView(Logger.Level.entries, "Select notification level").showPopup(
                        context,
                        this@hbox,
                        NotificationView.level
                    ) { lvl ->
                        NotificationView.level = lvl
                    }
                } else logWindow.show()
            }
            +Icon.Console.button(action = "Open console (Ctrl+T)") { shellWindow.show() }
            +Icon.SetupCode.button(action = "Edit setup code") { ev ->
                if (ev.isShiftDown) serverSetupCodeWindow.show()
                else serverTreeCodeWindow.show()
            }
            +Icon.Restart.button(action = "Restart server (F5)") { ev ->
                if (ev.isShiftDown) {
                    ServerOptionsPane(context, project.serverOptions).showDialog(context)
                } else {
                    project.rebootServer()
                }
            }
            +Icon.Browser.button(action = "Open help browser (F1)") { project.context[HelpBrowser].show() }
            +Icon.Flow.button(action = "Edit audio flow graph (Ctrl+Shift+F)") { flowGraphWindow.show() }
            +Icon.Settings.button(action = "Edit settings (Ctrl+Alt+S)") { settingsWindow.show() }
            +Icon.Knob.button(action = "Edit global controls (Ctrl+Shift+G)") { globalControlsWindow.show() }
            +Icon.Bus.button(action = "Show buses (Ctrl+B)") {
                busesWindow.show()
                busesWindow.requestFocus()
            }
            +Icon.Samples.button(action = "Show samples (Ctrl+F)") {
                samplesWindow.show()
                samplesWindow.requestFocus()
            }
            +Icon.Groups.button(action = "Show groups (Ctrl+G)") { groupsWindow.show() }
            if (mode == Mode.Laptop) {
                add(Icon.Details.button(action = "Edit object properties (P)") { showDetailPaneOfSelectedObject() }) {
                    disableProperty().bind(scoreView.selector.singleSelected.equalTo(null).asObservableValue())
                }
            }
            +Icon.Sync.button(action = "Sync with SuperCollider (Ctrl+Shift+S)") {
                project.syncWithSuperCollider()
            }
        }
    }

    private fun createPlayerBar(): HBox {
        playBtn = Icon.Play.button(action = "Start playback (Space)") { _ -> togglePlay() }
        stopBtn = Icon.Stop.button(action = "Pause playback (Space)") { stopPlayback() }
        recordingBtn = Icon.RecordInactive.button(action = "Activate Recording (Ctrl+R)") { ev ->
            if (ev.isShiftDown) {
                val currentSelected =
                    project.serverOptions.recordedBus?.get<BusObject>() ?: context[BusRegistry].getDefault()
                SearchableBusListView(context[BusRegistry], "Select bus to record to", rate = Rate.Audio).showPopup(
                    context,
                    anchorNode = recordingBtn,
                    initialOption = currentSelected
                ) { bus ->
                    project.serverOptions.recordedBus = bus.createReference()
                }
            } else toggleRecording()
        }
        val goToStartBtn = Icon.GoToStart.button(action = "Move play head to start (0)") {
            playback.movePlayHeadToStart()
        }
        return HBox(goToStartBtn, playBtn, stopBtn, recordingBtn)
    }

    private fun toggleRecording() {
        playback.recorder.toggleIsActive()
        if (!playback.recorder.isActive) {
            recordingBtn.tooltip = Tooltip("Start Recording (Ctrl+R)")
            recordingBtn.graphic = Icon.RecordInactive.getView()
        } else {
            recordingBtn.tooltip = Tooltip("Stop Recording (Ctrl+R)")
            recordingBtn.graphic = Icon.RecordActive.getView()
        }
    }

    private fun togglePlay() {
        if (!playback.player.isPlaying) {
            if (playback.player.play()) {
                playBtn.graphic = Icon.Pause.getView()
                playBtn.tooltip = Tooltip("Pause playback")
            }
        } else {
            playback.player.pause()
            playBtn.graphic = Icon.Play.getView()
            playBtn.tooltip = Tooltip("Start playback")
        }
    }

    private fun stopPlayback() {
        playBtn.graphic = Icon.Play.getView()
        playBtn.tooltip = Tooltip("Start playback")
        recordingBtn.graphic = Icon.RecordInactive.getView()
        recordingBtn.tooltip = Tooltip("Start recording")
        playback.player.reset()
    }

    private fun createFileBar() = HBox(
        Icon.Save.button(action = "Save Project (Ctrl+S)") { launcher.saveProject() },
        Icon.Open.button(action = "Open Project (Ctrl+O)") { launcher.openProject() },
        Icon.Create.button(action = "Create new Project (Ctrl+N)") { launcher.createNewProject() },
        Icon.Close.button(action = "Close project and open startup screen") { launcher.closeProject() })

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
                val view = context[ScoreObjectSelectionManager].singleSelected.now ?: return@addEventFilter
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
            addProjectShortcuts()
            addPlaybackShortcuts()
            addScoreNavigationShortcuts()
            addToolSelectionShortcuts()
            addGridRelatedShortcuts()
            addToolWindowShortcuts()
            addObjectManipulationShortcuts()
            addSelectionRelatedShortcuts()
            addServerShortcuts()
        }
    }

    private fun KeyEventHandlerBody<Unit>.addServerShortcuts() {
        on("F5") { project.rebootServer() }
        on("Shift+F5") { ServerOptionsPane(context, project.serverOptions).showDialog(context) }
        on("Ctrl+Shift+S") { project.syncWithSuperCollider() }
        on("Ctrl+Alt+T") { context[SuperColliderClient].run("s.plotTree;") }
        on("Ctrl+Shift+M") { context[SuperColliderClient].run("s.scope;") }
        on("Ctrl+Alt+M") { context[SuperColliderClient].run("FreqScope.new;") }
        on("Ctrl+M") {
            val numIns = project.serverOptions.numInputChannels
            val numOuts = project.serverOptions.numOutputChannels
            context[SuperColliderClient].run("ServerMeter.new(s, $numIns, $numOuts)")
        }
    }

    private fun KeyEventHandlerBody<Unit>.addSelectionRelatedShortcuts() {
        on("ESCAPE") {
            scoreView.endNewObject()
            scoreView.clearClipboard()
            if (!playback.player.isPlaying && playback.playHead.pane is ScoreObjectView) {
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
            val selected = scoreView.selector.singleSelected.now ?: return@on
            for (inst in selected.pane.score.instancesOf(selected.instance.obj)) {
                if (inst != selected.instance) {
                    val view = selected.pane.getObjectView(inst)
                    context[ScoreObjectSelectionManager].select(view, addToSelection = true)
                }
            }
        }
        on("C") { ev ->
            if (ev.isTargetTextInput) return@on
            val selected = scoreView.selector.singleSelected.now ?: return@on
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
            val view = context[ScoreObjectSelectionManager].singleSelected.now ?: return@on
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
                        val newInst = ScoreObjectInstance(clone, oldInst.position, oldInst.muted)
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

    private fun KeyEventHandlerBody<Unit>.addObjectManipulationShortcuts() {
        on("DELETE") { ev ->
            if (!ev.isTargetTextInput) {
                scoreView.removeSelected()
            }
        }
        on("M") { ev ->
            if (!ev.isTargetTextInput) {
                scoreView.selector.toggleMuteSelected()
            }
        }
        on("L") { ev ->
            if (!ev.isTargetTextInput) {
                val view = scoreView.selector.singleSelected.now ?: return@on
                view.createLoop()
            }
        }
        on("R") { ev ->
            if (!ev.isTargetTextInput) {
                for (obj in scoreView.selector.selectedObjects) {
                    if (obj is SynthObject) {
                        obj.reverse()
                    }
                }
            }
        }
        on("F2") { ev ->
            if (ev.isTargetTextInput) return@on
            val view = scoreView.selector.singleSelected.now ?: return@on
            val obj = view.instance.obj
            val name = NamePrompt(context[ScoreObjectRegistry], "New name for object", obj.name.now)
                .showDialog(context) ?: return@on
            obj.rename(name)
        }
        on("Ctrl?+Shift?+E") { ev ->
            if (ev.isTargetTextInput) return@on
            val inst = scoreView.selector.singleSelected.now?.instance ?: return@on
            val obj = inst.obj as? ScoreObjectGroup ?: return@on
            val times =
                if (ev.isControlDown) IntegerPrompt("Loop count", 1, 1..16).showDialog(context) ?: return@on
                else 1
            context.compoundEdit("Extend object group") {
                val duration = obj.duration
                obj.resize(
                    duration * (times + 1), obj.height,
                    ScoreObject.ResizeType.Regular, Direction.horizontal(RIGHT)
                )
                for (n in 1..times) {
                    for (subInst in obj.score.objectInstances.toList()) {
                        val pos = subInst.position + ObjectPosition(duration * n, zero)
                        val newInst = if (ev.isShiftDown) subInst.clone(pos) else subInst.duplicate(pos)
                        obj.score.addObject(newInst)
                    }
                }
            }
        }
        on("Shift+DELETE") { ev ->
            if (!ev.isTargetTextInput) {
                val view = scoreView.selector.singleSelected.now ?: return@on
                val inst = view.instance
                val obj = inst.obj as? ScoreObjectGroup ?: return@on
                val parentScore = inst.score!!
                context.compoundEdit("Move objects to parent score") {
                    for (subInst in obj.score.objectInstances.toList()) {
                        subInst.moveTo(inst.position + subInst.position)
                        parentScore.addObject(subInst)
                    }
                    inst.score!!.removeObject(inst)
                }
            }
        }
    }

    private fun KeyEventHandlerBody<Unit>.addToolWindowShortcuts() {
        on("Ctrl+G") { groupsWindow.show() }
        on("Ctrl+B") { busesWindow.show() }
        on("Ctrl+F") { samplesWindow.show() }
        on("Ctrl+I") { instrumentsWindow.show() }
        on("Ctrl+P") { processDefsWindow.show() }
        on("Ctrl+L") { logWindow.show() }
        on("Ctrl+Shift+G") { globalControlsWindow.show() }
        on("Ctrl+Shift+F") { flowGraphWindow.show() }
        on("Ctrl+Alt+S") { settingsWindow.show() }

        on("Shift+I") {
            val registry = context[InstrumentRegistry]
            SimpleSearchableRegistryView(registry, "Select instrument")
                .showPopup(context, initialOption = registry.selectedInstrument.now) { instr ->
                    registry.select(instr)
                }
        }

        on("F1") { context[HelpBrowser].show() }
        on("Ctrl+Shift+D") {
            val searchText = SimpleTextPrompt("Look up documentation", "").showDialog(context) ?: return@on
            context[HelpBrowser].searchDocumentation(searchText)
        }
        on("Ctrl+T") { shellWindow.show() }

        on("I") { ev ->
            val selected = context[ScoreObjectSelectionManager].singleSelected.now?.instance?.obj
            if (!ev.isTargetTextInput) {
                if (selected is SynthObject) {
                    instrumentsPane.editInstrument(selected.synthDef)
                } else if (selected is ProcessObject) {
                    processDefsPane.editProcessDef(selected.processDef)
                }
            }
        }
        if (mode == Mode.Laptop) {
            on("P") { ev ->
                if (!ev.isTargetTextInput) {
                    showDetailPaneOfSelectedObject()
                }
            }
        }
    }

    private fun KeyEventHandlerBody<Unit>.addPlaybackShortcuts() {
        on("Ctrl+SPACE") { togglePlay() }
        on("Shift+SPACE") {
            val time = playback.player.currentTime + playback.playHead.timeBlock.absolutePosition.time
            scoreView.display(time, time + scoreView.displayedDuration)
        }
        on("Ctrl+PERIOD") { stopPlayback() }
        on("Ctrl+R") { toggleRecording() }
    }

    private fun KeyEventHandlerBody<Unit>.addScoreNavigationShortcuts() {
        on("HOME") { scoreView.display(0.0.asTime, scoreView.displayedDuration) }
        on("Shift+HOME") { scoreView.displayWholeScore() }
        on("Ctrl+Shift?+DIGIT0") { ev ->
            if (ev.isShiftDown) {
                if (playback.player.isPlaying) return@on
                scoreView.display(0.0.asTime, scoreView.displayedDuration)
                playback.attachToMainScore()
            }
            playback.movePlayHeadToStart()
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
        on("T") { ev ->
            if (!ev.isTargetTextInput) {
                project.settings.displayTimeGrid.toggle()
            }
        }
        on("Alt?+Q") { ev ->
            if (ev.isAltDown || !ev.isTargetTextInput) {
                project.settings.snapEnabled.toggle()
            }
        }
        on("Alt+S") { project.settings.snapOption.now = SnapOption.Seconds }
        on("Alt+B") { project.settings.snapOption.now = SnapOption.Bars }
        on("Alt+N") { project.settings.snapOption.now = SnapOption.Beats }
        on("Alt+T") { project.settings.snapOption.now = SnapOption.Ticks }
    }

    private fun KeyEventHandlerBody<Unit>.addProjectShortcuts() {
        on("Ctrl+S") { launcher.saveProject() }
        on("Ctrl+O") { launcher.openProject() }
        on("Ctrl+N") { launcher.createNewProject() }
        on("Ctrl+Shift?+Q") { ev ->
            if (ev.isShiftDown) {
                launcher.saveProject()
                launcher.quitApplication()
            } else launcher.closeRequest()
        }
    }

    private fun setClipboard(selected: ScoreObjectView) {
        toolSelector.select(Tool.Pointer)
        scoreView.setClipboard(selected.instance.obj, selected)
    }

    private fun showDetailPaneOfSelectedObject() {
        val selected = scoreView.selector.singleSelected.now ?: return
        val pane = selected.getDetailPane()
        val name = selected.instance.obj.name.now
        val window = SubWindow(pane, "Configure $name", context, type = SubWindow.Type.Undecorated)
        window.show()
    }

    private val KeyEvent.isTargetTextInput get() = target is TextInputControl || target is Spinner<*>

    enum class Mode {
        Desktop, Laptop;
    }

    override fun close() {
        context[SuperColliderClient].quit()
        //TODO is there more cleanup to do?
    }

    companion object : PublicProperty<XenakisMainScreen> by publicProperty("XenakisUI")
}