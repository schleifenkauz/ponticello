package ponticello.ui.dock

import bundles.PublicProperty
import bundles.publicProperty
import bundles.set
import fxutils.*
import fxutils.actions.ActionBar
import fxutils.actions.ContextualizedAction
import fxutils.actions.collectActions
import fxutils.undo.UndoManager
import hextant.context.withoutUndo
import javafx.beans.binding.Bindings
import javafx.geometry.Insets
import javafx.geometry.Orientation
import javafx.scene.control.Button
import javafx.scene.control.SplitPane
import javafx.scene.image.ImageView
import javafx.scene.layout.*
import org.kordamp.ikonli.materialdesign2.MaterialDesignF
import ponticello.impl.Logger
import ponticello.model.player.ScorePlayer
import ponticello.model.project.PonticelloProject
import ponticello.model.project.UI_STATE
import ponticello.model.project.get
import ponticello.model.registry.ObjectList
import ponticello.model.tree.NodeTreePane
import ponticello.ui.actions.*
import ponticello.ui.dock.Side.*
import ponticello.ui.flow.MixerPane
import ponticello.ui.flow.TabbedAudioFlowsPane
import ponticello.ui.launcher.Activity
import ponticello.ui.launcher.PonticelloLauncher
import ponticello.ui.launcher.PonticelloMainActivity
import ponticello.ui.launcher.ProjectSelectorPrompt
import ponticello.ui.live.ConductorPane
import ponticello.ui.live.LauncherGridPane
import ponticello.ui.live.LiveObjectRegistryPane
import ponticello.ui.misc.*
import ponticello.ui.record.LiveBuffersPane
import ponticello.ui.registry.*
import ponticello.ui.score.NavigableScorePane
import ponticello.ui.score.ScoreObjectDetailPane
import ponticello.ui.score.ScoreObjectViewPane
import ponticello.ui.vc.VersionControlActions
import reaktive.value.now
import kotlin.reflect.KClass

class AppLayout(
    private val activity: PonticelloMainActivity,
    private val project: PonticelloProject,
    private val scoreView: NavigableScorePane,
    private val interactionConfigBar: InteractionConfigBar,
) : BorderPane(), ObjectList.Listener<ToolPane.Type> {
    private val sideBarLists = project[UI_STATE].sideBarStates.associateTo(mutableMapOf()) { state ->
        val toolPaneTypes = state.toolPanes.mapNotNull { t -> toolPaneType(t) }.distinct().toMutableList()
        state.side to ToolPaneTypeList(toolPaneTypes)
    }

    private val savedDividerPositions = mutableMapOf<Side, Double>()
    private var currentlyInSetup = true

    private val leftPane = SplitPane()
    private val rightPane = SplitPane()
    private val bottomPane = SplitPane()
    private val sidePanes = listOf(leftPane, rightPane, bottomPane)
    private val horizontalSplitter = SplitPane(scoreView)
    private val verticalSplitter = SplitPane(horizontalSplitter)

    private lateinit var leftBottomBar: ToolPaneActionBar
    private lateinit var leftTopBar: ToolPaneActionBar
    private lateinit var rightBar: ToolPaneActionBar
    private lateinit var topRightBar: ActionBar

    private val toolPanes = mutableListOf<ToolPane>()
    private val toolbar = createToolbar()

    val context get() = project.context

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

        for (box in toolbar.children) HBox.setHgrow(box, Priority.ALWAYS)

        setupToolPanes()
        for ((_, sideBarList) in sideBarLists) {
            sideBarList.addListener(this, initialize = false)
        }
        left = VBox(leftTopBar, infiniteSpace(), leftBottomBar) styleClass "dock-icons-bar"
        right = rightBar styleClass "dock-icons-bar"
        top = toolbar
        center = verticalSplitter
    }

    fun restorePaneSizes() {
        val sideBarStates = project[UI_STATE].sideBarStates
        for ((side, size, _, dividerPositions) in sideBarStates) {
            savedDividerPositions[side] = size
            restorePaneSize(side, size)
            val pane = getSidePane(side) ?: continue
            @Suppress("UsePropertyAccessSyntax")
            pane.setDividerPositions(*dividerPositions.toDoubleArray())
        }
    }

    fun toolPanes(): List<ToolPane> = toolPanes

    fun getToolPane(clazz: KClass<out ToolPane>, setup: Boolean = true): ToolPane? =
        toolPanes.find { it::class == clazz }?.also { p -> if (setup) p.setup() }

    fun getToolPane(type: ToolPane.Type): ToolPane? = toolPanes.find { it.type == type }

    inline fun <reified T : ToolPane> get(setup: Boolean = true) = getToolPane(T::class, setup) as T

    private fun setupToolPanes() = context.withoutUndo {
        val uiState = project[UI_STATE]
        val toolPaneSides = sideBarLists.entries.flatMap { (side, types) ->
            types.map { t -> t to side }
        }.toMap(mutableMapOf())
        for ((_, list) in sideBarLists) list.initialize(context)
        for (type in allToolPaneTypes) {
            if (type !in toolPaneSides) {
                sideBarLists.getOrPut(type.defaultSide) { ToolPaneTypeList.new(context) }.add(type)
                toolPaneSides[type] = type.defaultSide
            }
            val toolPane = type.createToolPane(project)
            toolPane.side!!.set(toolPaneSides.getValue(type))
            toolPanes.add(toolPane)
            val state = uiState.toolPaneStates.find { s -> s.uid == type.uid } ?: toolPane.defaultState()
            toolPane.initialize(this, state)
        }
        leftBottomBar = ToolPaneActionBar(this, sideBarLists.getValue(BOTTOM))
        leftTopBar = ToolPaneActionBar(this, sideBarLists.getValue(LEFT))
        rightBar = ToolPaneActionBar(this, sideBarLists.getValue(RIGHT))
        topRightBar.addActions(sideBarLists.getValue(TOP).map { p -> ToolPaneAction(getToolPane(p)!!) })
        currentlyInSetup = false
    }

    fun getToolPaneButton(type: ToolPane.Type): Button? {
        for (bar in listOf(leftTopBar, leftBottomBar, rightBar)) {
            if (type in bar.layout.source) {
                val box = bar.layout.getBox(type)
                return box.content as? Button
            }
        }
        return null
    }

    fun showDocked(toolPane: ToolPane) {
        val side = getSide(toolPane)
        val sidePane = getSidePane(side)
        if (sidePane == null) {
            Logger.warn("SidePane for ${toolPane.title} not found", Logger.Category.Layout)
            return
        }
        if (sidePane.items.contains(toolPane)) {
            Logger.warn("ToolPane ${toolPane.title} already showing on $side", Logger.Category.Layout)
            return
        }
        val types = sideBarLists.getValue(side)
        val idx = types.indexOf(toolPane.type)
        var insertIdx = sidePane.items.binarySearchBy(idx) { p -> types.indexOf((p as ToolPane).type) }
        insertIdx = -insertIdx - 1
        sidePane.items.add(insertIdx, toolPane)
        if (currentlyInSetup && project[UI_STATE].getSideBarState(side)?.isExpanded == false) return

        showSidePane(sidePane)
        if (sidePane.items.size != 1) {
            val pos1 = sidePane.dividerPositions[sidePane.items.size - 2]
            sidePane.setDividerPosition(sidePane.items.size - 1, (pos1 + 1) / 2)
        }
        for (p in sidePane.items.toList()) {
            p as ToolPane
            if (p == toolPane) continue
            if (p.isExclusive || toolPane.isExclusive) {
                p.setShowing(false)
            }
        }
    }

    private fun showSidePane(sidePane: SplitPane) {
        when (sidePane) {
            leftPane -> if (sidePane !in horizontalSplitter.items) {
                horizontalSplitter.items.add(0, sidePane)
                horizontalSplitter.setDividerPosition(0, savedDividerPositions[LEFT] ?: DEFAULT_SIDE_PANE_PORTION)
            }

            rightPane -> if (sidePane !in horizontalSplitter.items) {
                horizontalSplitter.items.add(sidePane)
                val dividerIndex = horizontalSplitter.items.size - 2
                val position = savedDividerPositions[RIGHT] ?: (1 - DEFAULT_SIDE_PANE_PORTION)
                horizontalSplitter.setDividerPosition(dividerIndex, position)
            }

            bottomPane -> if (sidePane !in verticalSplitter.items) {
                verticalSplitter.items.add(sidePane)
                verticalSplitter.setDividerPosition(0, savedDividerPositions[BOTTOM] ?: 0.66)
            }

            else -> throw AssertionError()
        }
    }

    fun setExclusive(toolPane: ToolPane) {
        if (!toolPane.isShowing.now) return
        val side = getSide(toolPane)
        val pane = getSidePane(side) ?: return
        pane.items.setAll(toolPane)
    }

    private fun getSide(toolPane: ToolPane): Side {
        val side = sideBarLists.keys.find { side -> toolPane.type in sideBarLists.getValue(side) }
            ?: error("No side found for ${toolPane.type}")
        return side
    }

    private fun getSidePane(side: Side): SplitPane? = when (side) {
        LEFT -> leftPane
        RIGHT -> rightPane
        BOTTOM -> bottomPane
        TOP -> null
    }

    fun hideDocked(toolPane: ToolPane) {
        val pane = sidePanes.find { pane -> toolPane in pane.items }
        if (pane == null) {
            Logger.warn("ToolPane ${toolPane.title} not showing", Logger.Category.Layout)
            return
        }
        pane.items.remove(toolPane)
        if (pane.items.isEmpty()) {
            hideSidePane(pane)
        }
    }

    private fun hideSidePane(pane: SplitPane) {
        if (pane.scene == null) return
        when (pane) {
            leftPane -> {
                savedDividerPositions[LEFT] = horizontalSplitter.dividerPositions[0]
                val rightDivider = horizontalSplitter.dividerPositions.getOrNull(1)
                horizontalSplitter.items.remove(leftPane)
                if (rightDivider != null) {
                    horizontalSplitter.setDividerPosition(0, rightDivider)
                }
            }

            rightPane -> {
                savedDividerPositions[RIGHT] =
                    horizontalSplitter.dividerPositions[horizontalSplitter.items.size - 2]
                horizontalSplitter.items.remove(rightPane)
            }

            bottomPane -> {
                savedDividerPositions[BOTTOM] = verticalSplitter.dividerPositions[0]
                verticalSplitter.items.remove(bottomPane)
            }

            else -> throw AssertionError()
        }
    }

    override fun removed(obj: ToolPane.Type, idx: Int) {
        val toolPane = getToolPane(obj) ?: error("ToolPane $obj not found")
        if (toolPane.isShowing.now) {
            hideDocked(toolPane)
        }
    }

    override fun added(obj: ToolPane.Type, idx: Int) {
        val toolPane = getToolPane(obj) ?: error("ToolPane $obj not found")
        if (toolPane.isShowing.now) {
            showDocked(toolPane)
        }
        toolPane.side!!.set(getSide(toolPane))
    }

    override fun moved(obj: ToolPane.Type, idx: Int) {
        removed(obj, idx)
        added(obj, idx)
    }

    private fun createProjectSelectorButton(): Button {
        val launcher = context[PonticelloLauncher]
        val projectSelector = ProjectSelectorPrompt(launcher)
        val button = selectorButton(project.name)
        button.setOnAction {
            val option = projectSelector.showDialog(button) ?: return@setOnAction
            option.openProject(launcher)
        }
        return button
    }

    private fun ponticelloIcon(): ImageView = ImageView(Activity.APP_ICON).apply {
        fitHeight = 24.0
        isPreserveRatio = true
        padding = Insets(0.0, 0.0, 0.0, 0.0)
    }

    private fun createToolbar(): Pane = BorderPane().apply {
        left = HBox(
            10.0,
            ponticelloIcon(),
            createProjectSelectorButton(),
            toolbarPart(ProjectActions.withContext(project)),
            toolbarPart(VersionControlActions.withContext(project)),
            toolbarPart(UndoRedoActions.withContext(scoreView.context[UndoManager])),
        ).centerChildren()
        val playerBar = toolbarPart(PlaybackActions.global.withContext(context[ScorePlayer.MAIN]))
        center = VBox(
            HBox(
                infiniteSpace(),
                interactionConfigBar styleClass "toolbar-part-segment",
                hspace(20.0),
                playerBar styleClass "toolbar-part-segment",
                hspace(20.0),
                scoreView.timeCodeView styleClass "toolbar-part-segment",
                infiniteSpace()
            ).centerChildren().neverVGrow()
        ).centerChildren().pad(2.0)
        topRightBar = toolbarPart(ServerActions.withContext(project))
        right = HBox(
            topRightBar,
            hspace(10.0),
            toolbarPart(WindowActions.all.withContext(activity))
        )
    } styleClass "toolbar"

    private fun getDividerPosition(side: Side) = when (side) {
        LEFT ->
            if (leftPane !in horizontalSplitter.items) savedDividerPositions[LEFT] ?: DEFAULT_SIDE_PANE_PORTION
            else horizontalSplitter.dividerPositions[0]

        RIGHT ->
            if (rightPane !in horizontalSplitter.items) savedDividerPositions[RIGHT] ?: (1 - DEFAULT_SIDE_PANE_PORTION)
            else horizontalSplitter.dividerPositions[horizontalSplitter.items.size - 2]

        BOTTOM ->
            if (bottomPane !in verticalSplitter.items) savedDividerPositions[BOTTOM] ?: DEFAULT_BOTTOM_PANE_PORTION
            else verticalSplitter.dividerPositions[0]

        TOP -> 0.0
    }

    private fun restorePaneSize(side: Side, size: Double) {
        when (side) {
            LEFT -> if (size != 0.0 && leftPane in horizontalSplitter.items) {
                horizontalSplitter.setDividerPosition(0, size)
            }

            RIGHT -> if (size != 0.0 && rightPane in horizontalSplitter.items) {
                horizontalSplitter.setDividerPosition(horizontalSplitter.items.size - 2, size)
            }

            BOTTOM -> if (size != 0.0 && bottomPane in verticalSplitter.items) {
                verticalSplitter.setDividerPosition(0, size)
            }

            TOP -> {}
        }
    }

    fun saveLayoutState() {
        val state = project[UI_STATE]
        state.sideBarStates = Side.entries.map { side ->
            val dividerPos = getDividerPosition(side)
            val pane = getSidePane(side)
            val dividerPositions = pane?.dividerPositions?.toList().orEmpty()
            val toolPanes = sideBarLists.getValue(side).map { t -> t.uid }
            val expanded = pane?.scene != null
            SideBarState(side, dividerPos, toolPanes, dividerPositions, expanded)
        }
        state.toolPaneStates = allToolPaneTypes.mapNotNull { type ->
            val toolPane = getToolPane(type)
            if (toolPane == null) {
                Logger.warn("ToolPane $type not found", Logger.Category.Layout)
                return@mapNotNull null
            }
            val s = toolPane.initialState ?: toolPane.defaultState()
            if (toolPane.isSetup) toolPane.saveState(s)
            s
        }
    }

    fun actions(): List<ContextualizedAction> =
        toolPanes.map { tp -> ToolPaneAction(tp) } + layoutActions.withContext(this)

    companion object : PublicProperty<AppLayout> by publicProperty("AppLayout") {
        private const val DEFAULT_SIDE_PANE_PORTION = 0.2
        private const val DEFAULT_BOTTOM_PANE_PORTION = 0.3

        val layoutActions = collectActions<AppLayout> {
            addAction("Hide all side bars") {
                shortcut("Shift+ESCAPE")
                icon(MaterialDesignF.FULLSCREEN)
                executes { layout ->
                    val sidePanes = layout.sidePanes
                    if (sidePanes.any { p -> p.scene != null }) {
                        for (pane in sidePanes) {
                            layout.hideSidePane(pane)
                        }
                    } else {
                        for (pane in sidePanes) {
                            if (pane.items.isNotEmpty()) {
                                layout.showSidePane(pane)
                            }
                        }
                    }
                }
            }
            addAction("Hide all tool-panes") {
                shortcut("Ctrl+ESCAPE")
                executes { layout ->
                    for (toolPane in layout.toolPanes) {
                        toolPane.setShowing(false)
                    }
                }
            }
        }

        private val allToolPaneTypes = buildList {
            //top
            add(LauncherGridPane)
            add(SettingsPane)

            //default left side-pane
            add(ClockRegistryPane)
            add(BusRegistryPane)
            add(ScoreObjectDetailPane)
            add(ConductorPane)

            //default right side-pane
            add(LogPane)
            add(BufferRegistryPane)
            add(InstrumentRegistryPane)

            add(GlobalPatternRegistryPane)
            add(LiveObjectRegistryPane)
            add(ScriptRegistryPane)
            add(OSCHookRegistryPane)
            add(NodeTreePane)

            add(HelpBrowser)

            //default bottom
//            add(AudioFlowsPane)
            add(TabbedAudioFlowsPane)
            add(MixerPane)
            add(ScoreObjectViewPane)
            add(ConsoleOutputPane)
            add(LiveBuffersPane)
        }.toMutableList()

        fun toolPaneType(uid: Int) = allToolPaneTypes.find { it.uid == uid }

        fun registerToolPane(type: ToolPane.Type) {
            allToolPaneTypes.add(type)
        }
    }
}