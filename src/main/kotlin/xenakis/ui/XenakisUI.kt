package xenakis.ui

import bundles.createBundle
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
import javafx.stage.StageStyle
import org.controlsfx.control.textfield.TextFields
import reaktive.Observer
import reaktive.value.binding.map
import reaktive.value.binding.not
import reaktive.value.forEach
import reaktive.value.fx.asObservableValue
import reaktive.value.now
import xenakis.model.XenakisProject
import xenakis.sc.view.SynthDefsEditorControl
import xenakis.ui.ToolSelector.Tool

class XenakisUI(private val stage: Stage, private val controller: XenakisController) : XenakisListener {
    val project get() = controller.currentProject

    val toolSelector = ToolSelector()

    private lateinit var serverSetupCodePane: CodePane
    private lateinit var beforePlayCodePane: CodePane
    private lateinit var synthDefsEditor: SynthDefsEditorControl
    private lateinit var buffersEditor: BuffersEditor
    private lateinit var scoreView: ScoreView
    private lateinit var flowGraphEditor: AudioFlowGraphEditor
    private lateinit var flowGraphWindow: Stage

    private lateinit var playBtn: Button
    private lateinit var stopBtn: Button
    private lateinit var recordBtn: Button

    lateinit var player: ScorePlayer
    private lateinit var shellWindow: Stage

    private val objectActionsBar = HBox(5.0)
    private lateinit var selectedObjectObserver: Observer

    private var displaysProject = false

    init {
        objectActionsBar.alwaysHGrow()
        objectActionsBar.centerChildrenVertically()
        controller.context[HelpBrowser] = HelpBrowser(controller.context)
        stage.scene = Scene(Pane())
        stage.scene.initHextantScene(controller.context)
    }

    override fun displayProject(project: XenakisProject) {
        serverSetupCodePane = CodePane("Server setup", project.serverSetup.control)
        beforePlayCodePane = CodePane("Play setup", project.beforePlay.control)
        synthDefsEditor = project.synthDefs.editor.control as SynthDefsEditorControl
        buffersEditor = BuffersEditor(project.buffers, project, controller)
        scoreView = ScoreView(project, this)
        flowGraphEditor = AudioFlowGraphEditor(project.flowGraph, project.context)
        flowGraphEditor.setPrefSize(800.0, 800.0)
        flowGraphWindow = SubWindow(flowGraphEditor, "Audio flow graph", project.context, style = StageStyle.DECORATED)

        player = ScorePlayer(scoreView, project, controller.client)
        shellWindow = SuperColliderShellController.createShellWindow(controller.client)

        selectedObjectObserver = scoreView.singleSelected.forEach { view ->
            if (view == null) objectActionsBar.children.clear()
            else objectActionsBar.children.setAll(view.header)
        }

        stage.scene.root = createLayout()
        stage.sizeToScene()
        stage.isResizable = true
        Platform.runLater { scoreView.displayWholeScore() }
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
                    val remove = showYesNoDialog("Project file does not exist. Remove from list?", default = true)
                    if (remove) {
                        controller.removeFromRecentProjects(proj)
                        recentProjects.children.remove(box)
                    }
                }
            }
            val removeBtn = Icon.Close.button(action = "Remove from list of recent projects") {
                val reallyRemove = showYesNoDialog("Remove project from list of recent projects?", default = true)
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
        stage.sizeToScene()
        stage.isResizable = false
    }

    private fun createLayout(): VBox {
        val leftSplitter = SplitPane(synthDefsEditor, buffersEditor)
        val rightSplitter = SplitPane(serverSetupCodePane, beforePlayCodePane)
        leftSplitter.orientation = Orientation.VERTICAL
        rightSplitter.orientation = Orientation.VERTICAL
        val horizontalSplitter = SplitPane(leftSplitter, scoreView, rightSplitter)
        horizontalSplitter.setDividerPositions(0.3, 0.8)
        val toolbar = createToolbar()
        objectActionsBar.prefWidthProperty().bind(toolbar.widthProperty().multiply(0.33))
        for (box in toolbar.children) HBox.setHgrow(box, Priority.ALWAYS)
        VBox.setVgrow(horizontalSplitter, Priority.ALWAYS)
        val layout = VBox(toolbar, horizontalSplitter)
        addShortcuts(layout)
        layout.setPrefSize(3000.0, 1200.0)
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
        val layoutBar = createLayoutBar() styleClass "toolbar-part"
        val miscBar = createMiscBar() styleClass "toolbar-part"
        return HBox(
            35.0,
            HBox(
                35.0,
                fileBar, undoRedoBar, playerBar,
                toolSelector styleClass "toolbar-part", layoutBar
            ), HBox(
                objectActionsBar styleClass "toolbar-part",
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

        return HBox(5.0, undo, redo)
    }

    private fun createMiscBar() = HBox(5.0,
        Icon.Console.button(action = "Open console") { shellWindow.show() },
        Icon.Restart.button(action = "Restart server") { project.rebootServer() },
        Icon.Browser.button(action = "Open help browser") { project.context[HelpBrowser].show() },
        Icon.Graph.button(action = "Edit audio flow graph") { flowGraphWindow.show() }
    )

    private fun createLayoutBar() = HBox(5.0,
        Icon.Horizontal.button(action = "Create horizontal group") {
            scoreView.score.addHorizontalGroup(scoreView.selectedObjects)
        },
        Icon.Vertical.button(action = "Create vertical group") {
            scoreView.score.addVerticalGroup(scoreView.selectedObjects)
        }
    )

    private fun createPlayerBar(): HBox {
        playBtn = Icon.Play.button(action = "Start playback") { _ -> togglePlay() }
        stopBtn = Icon.Stop.button(action = "Pause and free all nodes") { stop() }
        recordBtn = Icon.RecordInactive.button(action = "Start recording") { toggleRecord() }
        if (!controller.isSuperColliderReady) {
            playBtn.isDisable = true
            stopBtn.isDisable = true
            recordBtn.isDisable = true
        }
        return HBox(5.0, playBtn, stopBtn, recordBtn)
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

    private fun createFileBar() = HBox(5.0,
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

            on("Ctrl+SPACE") { togglePlay() }
            on("Ctrl+PERIOD") { stop() }

            on("Alt?+P") { toolSelector.select(Tool.Pointer) }
            on("Alt?+S") { toolSelector.select(Tool.Synth) }
            on("Alt?+T") { toolSelector.select(Tool.Task) }
            on("Alt?+E") { toolSelector.select(Tool.Envelope) }
            on("Alt?+M") { toolSelector.select(Tool.Memo) }
            on("Alt?+A") { toolSelector.select(Tool.AddTime) }

            on("DELETE") { scoreView.removeSelected() }
            on("ESCAPE") {
                scoreView.clearNewShape()
                scoreView.deselectAll()
            }
            on("Ctrl+D") {
                val view = scoreView.singleSelected.now
                if (view is SynthObjectView) {
                    view.openControlAssignment()
                }
            }
            on("Ctrl+M") {
                scoreView.toggleMuteSelected()
            }
            on("Alt+L") {
                val view = scoreView.singleSelected.now ?: return@on
                view.createLoop()
            }
            on("Alt+SPACE") {
                val view = scoreView.singleSelected.now ?: return@on
                view.playMyObject()
            }

            on("Ctrl+Alt+T") { controller.client.postAsync("s.plotTree;") }
            on("F1") { controller.context[HelpBrowser].show() }
            on("Ctrl+Shift+D") {
                val searchText = showTextInputDialog("Look up documentation", controller.context) ?: return@on
                controller.context[HelpBrowser].searchDocumentation(searchText)
            }
            on("Alt+C") { shellWindow.show() }
            on("Alt+G") { flowGraphWindow.show() }
            on("F5") { controller.restartScSynth() }

            on("Ctrl?+C") {
                scoreView.copySelected()
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
}