package xenakis.ui

import hextant.fx.Stylesheets
import hextant.fx.label
import javafx.geometry.Pos
import javafx.scene.Scene
import javafx.scene.control.Tooltip
import javafx.scene.input.KeyCode
import javafx.scene.input.KeyCodeCombination
import javafx.scene.input.KeyCombination.CONTROL_DOWN
import javafx.scene.input.KeyCombination.SHIFT_DOWN
import javafx.scene.layout.HBox
import javafx.scene.layout.Pane
import javafx.scene.layout.Priority
import javafx.scene.layout.VBox
import javafx.stage.Stage
import javafx.stage.StageStyle
import org.controlsfx.control.textfield.TextFields
import xenakis.impl.ZoomableScrollPane
import xenakis.model.XenakisProject

class XenakisUI(private val stage: Stage, private val controller: XenakisController) : XenakisListener {
    val project get() = controller.currentProject

    val toolSelector = ToolSelector()

    lateinit var synthDefsEditor: SynthDefsEditor
    lateinit var scoreView: ScoreView
    lateinit var flowGraphEditor: AudioFlowGraphEditor
    lateinit var flowGraphWindow: Stage

    lateinit var player: ScorePlayer
    lateinit var shellWindow: Stage

    init {
        stage.scene = Scene(Pane())
        controller.context[Stylesheets].manage(stage.scene)
    }

    override fun displayProject(project: XenakisProject) {
        synthDefsEditor = SynthDefsEditor(project)
        scoreView = ScoreView(project, this)
        flowGraphEditor = AudioFlowGraphEditor(project.flowGraph, project.context)
        flowGraphEditor.setPrefSize(800.0, 800.0)
        flowGraphWindow = flowGraphEditor.makeWindow("Audio flow graph", project.context, StageStyle.DECORATED)

        player = ScorePlayer(scoreView, controller.client)
        shellWindow = SuperColliderShellController.createShellWindow(controller.client)
        stage.scene.root = createLayout()
        stage.sizeToScene()
    }

    override fun displayStartupScreen() {
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
            val box = VBox(name, path).styleClass("project-box")
            box.setOnMouseClicked {
                controller.openProject(proj)
            }
            recentProjects.children.add(box)
        }
        val top = HBox(searchIcon, searchField, btnOpen, createNew).styleClass("startup-screen-top-bar")
        val layout = VBox(top, recentProjects).styleClass("startup-screen")
        stage.scene.root = layout
        stage.sizeToScene()
    }

    private fun createLayout(): VBox {
        val scrollPane = ZoomableScrollPane(scoreView)
        val toolbar = createToolbar()
        for (box in toolbar.children) HBox.setHgrow(box, Priority.ALWAYS)
        VBox.setVgrow(scrollPane, Priority.ALWAYS)
        val layout = VBox(toolbar, scrollPane)
        addShortcuts(layout)
        return layout
    }

    private fun createToolbar(): HBox {
        val playerBar = createPlayerBar()
        val fileBar = createFileBar()
        val miscBar = createMiscBar()
        miscBar.alignment = Pos.CENTER_RIGHT
        return HBox(20.0, fileBar, toolSelector, playerBar, miscBar)
            .styleClass("toolbar")
    }

    private fun createMiscBar() = HBox(5.0,
        Icon.Console.button(action = "Open console") { shellWindow.show() },
        Icon.Restart.button(action = "Restart server") { controller.restartSuperCollider() },
        Icon.AddTime.button(action = "Add time") { controller.addTime() },
        Icon.Graph.button(action = "Edit audio flow graph") { flowGraphWindow.show() }
    )

    private fun createPlayerBar(): HBox {
        val playBtn = Icon.Play.button(action = "Start playback") { btn ->
            if (!player.isPlaying) {
                btn.graphic = Icon.Pause.getView()
                btn.tooltip = Tooltip("Pause playback")
                player.play()
            } else {
                btn.graphic = Icon.Play.getView()
                btn.tooltip = Tooltip("Start playback")
                player.pause()
            }
        }
        return HBox(
            5.0,
            playBtn,
            Icon.Stop.button(action = "Pause and free all nodes") {
                player.pause()
                player.reset()
                playBtn.graphic = Icon.Play.getView()
                playBtn.tooltip = Tooltip("Start playback")
            },
            Icon.RecordInactive.button(action = "Start recording") { btn ->
                player.toggleRecording()
                if (player.isRecording) {
                    btn.graphic = Icon.RecordActive.getView()
                    btn.tooltip = Tooltip("Finish recording")
                } else {
                    btn.graphic = Icon.RecordInactive.getView()
                    btn.tooltip = Tooltip("Start recording")
                }
            }
        )
    }

    private fun createFileBar() = HBox(5.0,
        Icon.Save.button(action = "Save Project") { controller.saveProject() },
        Icon.Open.button(action = "Open Project") { controller.openProject() },
        Icon.Create.button(action = "Create new Project") { controller.createNewProject() },
        Icon.Export.button(action = "Export as SuperCollider script") { controller.exportAsScript() },
        Icon.Close.button(action = "Close project and open startup screen") { controller.closeProject() }
    )

    private fun addShortcuts(layout: VBox) {
        layout.setOnKeyReleased { ev ->
            when {
                KeyCodeCombination(KeyCode.SPACE, CONTROL_DOWN).match(ev) -> player.play()
                KeyCodeCombination(KeyCode.PERIOD, CONTROL_DOWN).match(ev) -> player.pause()
                KeyCodeCombination(KeyCode.DELETE).match(ev) -> scoreView.removeSelected()
                KeyCodeCombination(KeyCode.ESCAPE).match(ev) -> scoreView.clearNewShape()
                KeyCodeCombination(KeyCode.S, CONTROL_DOWN).match(ev) -> controller.saveProject()
                KeyCodeCombination(KeyCode.O, CONTROL_DOWN).match(ev) -> controller.openProject()
                KeyCodeCombination(KeyCode.N, CONTROL_DOWN).match(ev) -> controller.createNewProject()
                KeyCodeCombination(KeyCode.P).match(ev) -> toolSelector.select(ToolSelector.Tool.Pointer)
                KeyCodeCombination(KeyCode.F5).match(ev) -> controller.restartSuperCollider()
                KeyCodeCombination(KeyCode.V, CONTROL_DOWN).match(ev) -> controller.showServerWindow()
                KeyCodeCombination(KeyCode.R, CONTROL_DOWN).match(ev) -> {
                    controller.client.post("s.record")
                }

                KeyCodeCombination(KeyCode.R, CONTROL_DOWN, SHIFT_DOWN).match(ev) -> {
                    controller.client.post("s.stopRecording;")
                }
            }
            ev.consume()
        }
    }
}