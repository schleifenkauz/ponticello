package ponticello.ui.dock

import bundles.PublicProperty
import bundles.publicProperty
import bundles.set
import fxutils.actions.ContextualizedAction
import fxutils.actions.action
import fxutils.actions.makeButton
import fxutils.hspace
import fxutils.infiniteSpace
import fxutils.styleClass
import fxutils.undo.UndoManager
import javafx.beans.binding.Bindings
import javafx.geometry.Orientation
import javafx.scene.control.SplitPane
import javafx.scene.input.KeyEvent
import javafx.scene.input.MouseButton
import javafx.scene.input.MouseEvent
import javafx.scene.input.TransferMode
import javafx.scene.layout.*
import ponticello.impl.Logger
import ponticello.model.Settings
import ponticello.model.player.ScorePlayer
import ponticello.model.project.*
import ponticello.ui.actions.*
import ponticello.ui.dock.ToolPaneState.Side.*
import ponticello.ui.flow.AudioFlowPane
import ponticello.ui.impl.makeToolWindow
import ponticello.ui.launcher.PonticelloLauncher
import ponticello.ui.live.LauncherGridPane
import ponticello.ui.live.LiveTaskRegistryPane
import ponticello.ui.misc.ConsoleOutputPane
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
    private val timeCodeView: TimeCodeView,
) : BorderPane() {
    private val leftPane = SplitPane()
    private val rightPane = SplitPane()
    private val bottomPane = SplitPane()
    private val horizontalSplitter = SplitPane(scoreView)
    private val verticalSplitter = SplitPane(horizontalSplitter)
    private val leftBottomBar = VBox() styleClass "dock-icons"
    private val leftTopBar = VBox() styleClass "dock-icons"
    private val rightBar = VBox().styleClass("dock-icons", "dock-icons-bar")
    private lateinit var topRightBar: HBox
    private val actions = mutableListOf<ContextualizedAction>()
    private val toolPanes = createToolPanes()
    private val toolbar = createToolbar()

    val context get() = project.context

    private val paneSizes = project[UI_STATE].paneSizes.toMutableMap()

    val settingsWindow by lazy { context.makeToolWindow(SettingsPane(context[Settings]), "Settings") }

    init {
        styleClass("app-layout")
        context[AppLayout] = this

        leftPane.managedProperty().bind(leftPane.visibleProperty())
        rightPane.managedProperty().bind(rightPane.visibleProperty())
        bottomPane.managedProperty().bind(bottomPane.visibleProperty())

        leftPane.orientation = Orientation.VERTICAL
        rightPane.orientation = Orientation.VERTICAL
        verticalSplitter.orientation = Orientation.VERTICAL

        leftPane.visibleProperty().bind(Bindings.isNotEmpty(leftPane.items))
        leftPane.managedProperty().bind(leftPane.visibleProperty())

        rightPane.visibleProperty().bind(Bindings.isNotEmpty(rightPane.items))
        rightPane.managedProperty().bind(rightPane.visibleProperty())

        bottomPane.visibleProperty().bind(Bindings.isNotEmpty(bottomPane.items))
        bottomPane.managedProperty().bind(bottomPane.visibleProperty())

        left = VBox(leftTopBar, infiniteSpace(), leftBottomBar) styleClass "dock-icons-bar"
        right = rightBar
        top = toolbar
        center = verticalSplitter

        for (box in toolbar.children) HBox.setHgrow(box, Priority.ALWAYS)

        setupToolPanes()
    }

    fun getToolPane(title: String) = toolPanes.find { it.title == title }?.also { p -> p.setup() }

    fun getToolPane(clazz: KClass<out ToolPane>): ToolPane? =
        toolPanes.find { it::class == clazz }?.also { p -> p.setup() }

    inline fun <reified T : ToolPane> get() = getToolPane(T::class) as T

    private fun createToolPanes() = buildList {
        //default top
        add(LauncherGridPane(project[LAUNCHER_GRID]))

        //default left side-pane
        add(ClockRegistryPane(project[CLOCKS]))
        add(BusRegistryPane(project.busses))
        add(ScoreObjectRegistryPane(project.objects))

        //default right side-pane
        add(LogPane(Logger))
        add(BufferRegistryPane(project.buffers))
        add(InstrumentRegistryPane(project.instruments))

        add(GlobalPatternRegistryPane(project.patterns))
        add(LiveTaskRegistryPane(project[LIVE_TASKS]))
        add(ScriptRegistryPane(project.scripts))

        //default bottom
        add(AudioFlowPane(project.flows))

        add(ConsoleOutputPane(project.client))
    }

    private fun setupToolPanes() {
        for (toolPane in toolPanes) {
            val state = project[UI_STATE].toolPaneStates[toolPane.title] ?: toolPane.defaultState()
            toolPane.initialize(this, state)
            val iconBar = when (state.side) {
                LEFT -> leftTopBar
                RIGHT -> rightBar
                BOTTOM -> leftBottomBar
                TOP -> topRightBar
            }
            val action = toolPaneIconAction(toolPane)
            actions.add(action.withContext(Unit))
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
                ev is KeyEvent -> toolPane.handleShortcut(ev)
            }
        }
    }

    fun showToolPaneConfigMenu(toolPane: ToolPane, ev: MouseEvent) {
        TODO()
    }

    fun showDocked(toolPane: ToolPane) {
        val sidePane = when (toolPane.side) {
            LEFT -> leftPane
            RIGHT -> rightPane
            BOTTOM -> bottomPane
            TOP -> throw AssertionError()
        }
        sidePane.items.add(toolPane)
        if (sidePane.items.size == 1) {
            when (toolPane.side) {
                LEFT -> {
                    horizontalSplitter.items.add(0, sidePane)
                    horizontalSplitter.setDividerPosition(0, paneSizes[LEFT] ?: DEFAULT_SIDE_PANE_PORTION)
                }

                RIGHT -> {
                    horizontalSplitter.items.add(sidePane)
                    val dividerIndex = horizontalSplitter.items.size - 2
                    val position = paneSizes[RIGHT] ?: (1 - DEFAULT_SIDE_PANE_PORTION)
                    horizontalSplitter.setDividerPosition(dividerIndex, position)
                }

                BOTTOM -> {
                    verticalSplitter.items.add(sidePane)
                    verticalSplitter.setDividerPosition(0, paneSizes[BOTTOM] ?: 0.66)
                }

                else -> throw AssertionError()
            }
        } else {
            val pos1 = sidePane.dividerPositions[sidePane.items.size - 2]
            sidePane.setDividerPosition(sidePane.items.size - 1, (pos1 + 1) / 2)
        }
        for (p in sidePane.items) {
            p as ToolPane
            if (p == toolPane) continue
            if (p.isExclusive || toolPane.isExclusive) {
                p.setShowing(false)
            }
        }
    }

    fun hideDocked(toolPane: ToolPane) {
        val pane = when (toolPane.side) {
            LEFT -> leftPane
            RIGHT -> rightPane
            BOTTOM -> bottomPane
            TOP -> throw AssertionError()
        }
        pane.items.remove(toolPane)
        if (pane.items.isEmpty()) {
            when (toolPane.side) {
                LEFT -> horizontalSplitter.items.remove(leftPane)
                RIGHT -> horizontalSplitter.items.remove(rightPane)
                BOTTOM -> verticalSplitter.items.remove(bottomPane)
                else -> throw AssertionError()
            }
        }
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
            interactionConfigBar styleClass "toolbar-part-segment",
            hspace(20.0),
            playerBar styleClass "toolbar-part-segment",
            hspace(20.0),
            timeCodeView,
            infiniteSpace()
        )
        val toolWindowActions = ToolWindowActions.withContext(this@AppLayout)
        val serverActions = ServerActions.withContext(project)
        topRightBar = toolbarPart(toolWindowActions + serverActions)
        right = HBox(
            topRightBar,
            hspace(50.0),
            toolbarPart(QuitAction.withContext(launcher))
        )
    } styleClass "toolbar"

    fun actions(): List<ContextualizedAction> = actions

    companion object : PublicProperty<AppLayout> by publicProperty("AppLayout") {
        private const val DEFAULT_SIDE_PANE_PORTION = 0.2
    }
}