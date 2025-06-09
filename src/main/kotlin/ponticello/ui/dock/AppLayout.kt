package ponticello.ui.dock

import bundles.PublicProperty
import bundles.publicProperty
import bundles.set
import fxutils.actions.action
import fxutils.actions.makeButton
import fxutils.hspace
import fxutils.infiniteSpace
import fxutils.styleClass
import fxutils.undo.UndoManager
import javafx.scene.control.SplitPane
import javafx.scene.input.MouseButton
import javafx.scene.input.MouseEvent
import javafx.scene.input.TransferMode
import javafx.scene.layout.*
import ponticello.impl.Logger
import ponticello.model.Settings
import ponticello.model.player.ScorePlayer
import ponticello.model.project.*
import ponticello.ui.actions.*
import ponticello.ui.flow.AudioFlowPane
import ponticello.ui.impl.makeToolWindow
import ponticello.ui.launcher.PonticelloLauncher
import ponticello.ui.live.LiveTaskRegistryPane
import ponticello.ui.misc.InteractionConfigBar
import ponticello.ui.misc.LogPane
import ponticello.ui.misc.SettingsPane
import ponticello.ui.registry.*
import ponticello.ui.score.NavigableScorePane
import ponticello.ui.score.TimeCodeView
import kotlin.reflect.KClass

class AppLayout(
    private val launcher: PonticelloLauncher,
    private val project: PonticelloProject,
    private val scoreView: NavigableScorePane,
    private val interactionConfigBar: InteractionConfigBar,
    private val timeCodeView: TimeCodeView
) : BorderPane() {
    private val leftPane = SplitPane()
    private val rightPane = SplitPane()
    private val bottomPane = SplitPane()
    private val horizontalSplitter = SplitPane(leftPane, scoreView, rightPane)
    private val verticalSplitter = SplitPane(horizontalSplitter, bottomPane)
    private val leftBottomBar = VBox()
    private val leftTopBar = VBox()
    private val rightBar = VBox()
    private lateinit var rightTopBar: HBox
    private val toolPanes = createToolPanes()
    private val toolbar = createToolbar()

    val context get() = project.context

    val settingsWindow by lazy { context.makeToolWindow(SettingsPane(context[Settings], context), "Settings") }

    init {
        styleClass("app-layout")
        context[AppLayout] = this

        leftPane.managedProperty().bind(leftPane.visibleProperty())
        rightPane.managedProperty().bind(rightPane.visibleProperty())
        bottomPane.managedProperty().bind(bottomPane.visibleProperty())

        left = VBox(leftTopBar, leftBottomBar)
        right = rightBar
        top = toolbar
        center = verticalSplitter

        for (box in toolbar.children) HBox.setHgrow(box, Priority.ALWAYS)

        setupToolPanes()
    }

    fun getToolPane(title: String) = toolPanes.find { it.title == title }

    fun getToolPane(clazz: KClass<out ToolPane>) = toolPanes.find { it::class == clazz }

    inline fun <reified T: ToolPane> get() = getToolPane(T::class) as T

    private fun createToolPanes() = buildList {
        add(InstrumentRegistryPane(project.instruments))
        add(BusRegistryPane(project.busses))
        add(BufferRegistryPane(project.buffers))
        add(GlobalPatternRegistryPane(project.patterns))
        add(LiveTaskRegistryPane(project[LIVE_TASKS]))
        add(ScriptRegistryPane(project.scripts))
        add(ClockRegistryPane(project[CLOCKS]))
        add(ScoreObjectRegistryPane(project.objects))
        add(AudioFlowPane(project.flows))
        add(LogPane(Logger))
    }

    private fun setupToolPanes() {
        for (toolPane in toolPanes) {
            val state = project[UI_STATE].toolPaneStates[toolPane.title] ?: toolPane.defaultState()
            toolPane.initialize(this, state)
            val iconBar = when (state.side) {
                ToolPaneState.Side.LEFT -> leftTopBar
                ToolPaneState.Side.RIGHT -> rightBar
                ToolPaneState.Side.BOTTOM -> leftBottomBar
                ToolPaneState.Side.TOP -> rightTopBar
            }
            val action = toolPaneIconAction(toolPane)
            val button = action.makeButton("large-icon-button")
            iconBar.children.add(button)
        }
    }

    private fun toolPaneIconAction(toolPane: ToolPane) = action<Unit>(toolPane.title) {
        val icon = toolPane.icon
        if (icon != null) icon(icon)
        shortcuts(*toolPane.shortcuts)
        toggleState { toolPane.isShowing }
        executes { _, ev ->
            when {
                ev is MouseEvent && ev.button == MouseButton.PRIMARY -> toolPane.toggleShowing()
                ev is MouseEvent && ev.button == MouseButton.SECONDARY -> showToolPaneConfigMenu(toolPane, ev)
            }
        }
    }

    fun showToolPaneConfigMenu(toolPane: ToolPane, ev: MouseEvent) {
        TODO()
    }

    fun showDocked(toolPane: ToolPane) {
        val pane = when (toolPane.side) {
            ToolPaneState.Side.LEFT -> leftPane
            ToolPaneState.Side.RIGHT -> rightPane
            ToolPaneState.Side.BOTTOM -> bottomPane
            ToolPaneState.Side.TOP -> throw AssertionError()
        }
        for (p in pane.items) {
            p as ToolPane
            if (p.isExclusive || toolPane.isExclusive) {
                p.setShowing(false)
            }
        }
        pane.items.add(toolPane)
        if (pane.items.size == 1) {
            pane.isVisible = true
        } else {
            val pos1 = pane.dividerPositions[pane.items.size - 2]
            pane.setDividerPosition(pane.items.size - 1, (pos1 + 1) / 2)
        }
    }

    fun hideDocked(toolPane: ToolPane) {
        val pane = when (toolPane.side) {
            ToolPaneState.Side.LEFT -> leftPane
            ToolPaneState.Side.RIGHT -> rightPane
            ToolPaneState.Side.BOTTOM -> bottomPane
            ToolPaneState.Side.TOP -> throw AssertionError()
        }
        pane.items.remove(toolPane)
    }

    private fun createToolbar(): Pane = BorderPane().apply {
        left = HBox(
            10.0,
            toolbarPart(ProjectActions.withContext(launcher)),
            toolbarPart(UndoRedoActions.withContext(scoreView.context[UndoManager])),
        )
        val playerBar = toolbarPart(PlaybackActions.global.withContext(context[ScorePlayer.MAIN]))
        val recordBtn = playerBar.getButton(PlaybackActions.toggleRecording)
        recordBtn.setOnDragDetected { ev ->
            val db = recordBtn.startDragAndDrop(TransferMode.COPY)
            db.setContent(mapOf(PlaybackActions.RECORD_BUTTON to "<>"))
            ev.consume()
        }
        center = HBox(
            infiniteSpace(),
            interactionConfigBar,
            hspace(20.0),
            playerBar,
            hspace(20.0),
            timeCodeView,
            infiniteSpace()
        )
        val toolWindowActions = ToolWindowActions.withContext(this@AppLayout)
        val serverActions = ServerActions.withContext(project)
        right = HBox(
            toolbarPart(toolWindowActions + serverActions),
            hspace(50.0),
            toolbarPart(QuitAction.withContext(launcher))
        )
    } styleClass "toolbar"

    companion object: PublicProperty<AppLayout> by publicProperty("AppLayout")
}