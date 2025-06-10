package ponticello.ui.live

import fxutils.*
import fxutils.actions.*
import fxutils.controls.CheckBox
import fxutils.controls.SliderBar
import fxutils.prompt.SimpleSearchableListView
import fxutils.undo.UndoManager
import fxutils.undo.VariableEdit
import javafx.scene.Node
import javafx.scene.control.Label
import javafx.scene.input.*
import javafx.scene.layout.GridPane
import javafx.scene.layout.HBox
import javafx.scene.layout.Region
import javafx.scene.layout.VBox
import kotlinx.serialization.Contextual
import org.kordamp.ikonli.Ikon
import org.kordamp.ikonli.codicons.Codicons
import org.kordamp.ikonli.materialdesign2.MaterialDesignA
import org.kordamp.ikonli.materialdesign2.MaterialDesignE
import org.kordamp.ikonli.materialdesign2.MaterialDesignG
import org.kordamp.ikonli.materialdesign2.MaterialDesignR
import ponticello.impl.one
import ponticello.impl.toDecimal
import ponticello.impl.zero
import ponticello.model.flow.AudioFlow
import ponticello.model.flow.AudioFlows
import ponticello.model.live.ItemTarget
import ponticello.model.live.LauncherGrid
import ponticello.model.live.LauncherGrid.GridItemReference
import ponticello.model.live.LiveTaskObject
import ponticello.model.obj.*
import ponticello.model.player.ScorePlayer
import ponticello.model.project.*
import ponticello.model.registry.BufferRegistry
import ponticello.model.registry.ObjectReference
import ponticello.model.registry.ScoreObjectRegistry
import ponticello.model.registry.reference
import ponticello.model.score.ScoreObject
import ponticello.model.score.ScoreObjectGroup
import ponticello.sc.NumericalControlSpec
import ponticello.sc.ParameterType
import ponticello.sc.Warp
import ponticello.ui.actions.PlaybackActions
import ponticello.ui.actions.UndoRedoActions
import ponticello.ui.dock.AppLayout
import ponticello.ui.dock.Side
import ponticello.ui.dock.ToolPane
import ponticello.ui.dock.ToolPaneState
import ponticello.ui.impl.DEFAULT_SCENE_FILL
import ponticello.ui.impl.getFrom
import ponticello.ui.registry.ScoreObjectRegistryPane
import ponticello.ui.registry.ScriptRegistryPane
import ponticello.ui.registry.SearchableParameterDefListView
import ponticello.ui.score.FlowGroupManager
import reaktive.value.binding.*
import reaktive.value.now
import reaktive.value.reactiveValue

class LauncherGridPane(
    private val grid: LauncherGrid,
) : ToolPane(), LauncherGrid.View {
    private val context = grid.context
    private val boxes = mutableMapOf<LauncherGrid.GridItem, Region>()
    override val content = GridPane().styleClass("launcher-grid-pane")
    override val headerContent: Node = setupHeader()
    override val type: Type get() = LauncherGridPane
    override val shortcuts: Array<String>
        get() = arrayOf("Ctrl+G")

    override val headerActions: List<ContextualizedAction> = actions.withContext(grid)

    override fun defaultState(): ToolPaneState = ToolPaneState.window

    override fun afterSetup() {
        preparePlayers()
        setupGridPane()
        grid.addView(this)
    }

    private fun preparePlayers() {
        for (item in grid.items()) {
            val target = item.target
            if (target is ItemTarget.Player) {
                target.preparePlayer()
            }
        }
    }

    private fun setupHeader(): HBox {
        val availableTargets = LauncherGrid.Target.options(context)
        val targetSelector = SimpleSearchableListView(availableTargets, "Choose target")
            .selectorButton(grid.target, undoManager = context[UndoManager])
        val headerContent = HBox(5.0, Label("Target: "), targetSelector).centerChildren()
        return headerContent
    }

    private fun setupGridPane() {
        for (item in grid.items()) {
            val box = display(item)
            boxes[item] = box
            val (i, j) = grid.getGridIndices(item)
            content.add(box, j, i)
        }
    }

    override fun updateItem(item: LauncherGrid.GridItem) {
        content.children.remove(boxes[item])
        val box = display(item)
        boxes[item] = box
        val (i, j) = grid.getGridIndices(item)
        content.add(box, j, i)
    }

    private fun display(item: LauncherGrid.GridItem): VBox {
        val target = item.target
        val buttonText = if (target is ItemTarget.None) "Select Target" else item.target.toString()
        val button = button(buttonText)
        button.setOnAction {
            val listView = SimpleSearchableListView(ItemTarget.options(context), "Choose item target")
            val newTarget = listView.showPopup(button) ?: return@setOnAction
            item.target = newTarget
        }
        val actions = itemActions.withContext(target) + detailsAction.withContext(item)
        val actionBar = ActionBar(actions, buttonStyle = "medium-icon-button")
        val centeredActionBar = HBox(infiniteSpace(), actionBar, infiniteSpace())
        val box = VBox(button, centeredActionBar).styleClass("launcher-grid-item").centerChildren()
        if (target is ItemTarget.Object) {
            val obj = target.ref.get()
            if (obj != null) {
                val spec = NumericalControlSpec(zero, zero, one, 0.01.toDecimal(), zero, Warp.Linear)
                val scoreYSlider = SliderBar(
                    obj.liveConfig.yPosition, "Score Y", spec.converter(),
                    undoManager = context[UndoManager],
                ).setFixedWidth(120.0)
                box.children.add(scoreYSlider)
            }
        }
        addMouseActions(box, item)
        setupDragAndDrop(box, item)
        box.userData = box.bindPseudoClassState("active", target.isActive)
        return box
    }

    private fun addMouseActions(box: VBox, item: LauncherGrid.GridItem) {
        box.setOnMousePressed { ev ->
            if (ev.target == box && ev.modifiers.isEmpty() && ev.button == MouseButton.PRIMARY) {
                grid.noteOn(item, velocity = 64)
            }
        }
        box.setOnMouseClicked { ev ->
            if (ev.target == box && ev.modifiers.isEmpty() && ev.button == MouseButton.SECONDARY) {
                item.target = ItemTarget.None
            }
        }
        box.setOnMouseReleased { ev ->
            if (ev.target == box && ev.modifiers.isEmpty() && ev.button == MouseButton.PRIMARY) {
                grid.noteOff(item)
            }
        }
    }

    private fun setupDragAndDrop(box: VBox, item: LauncherGrid.GridItem) {
        box.setOnDragDetected { ev ->
            if (ev.modifiers != setOf(Ctrl)) return@setOnDragDetected
            val db = box.startDragAndDrop(TransferMode.MOVE)
            val ref = item.reference()
            db.setContent(mapOf(GridItemReference.DATA_FORMAT to ref))
            ev.consume()
        }
        box.setupDropArea(canDropOnItem(item), dropOn(item))
    }

    private fun dropOn(item: LauncherGrid.GridItem) = drop@{ ev: DragEvent ->
        val db = ev.dragboard
        when {
            db.hasContent(GridItemReference.DATA_FORMAT) -> {
                val ref = db.getContent(GridItemReference.DATA_FORMAT) as GridItemReference
                val droppedItem = ref.getItem(grid)
                grid.swap(item, droppedItem)
            }

            db.hasContent(PlaybackActions.RECORD_BUTTON) -> {
                item.target = ItemTarget.ToggleRecording
            }

            db.hasContent(AudioFlow.DATA_FORMAT) -> {
                val ref = db.getContent(AudioFlow.DATA_FORMAT) as AudioFlows.FlowReference
                item.target = ItemTarget.Flow(ref)
            }

            db.hasContent(ScoreObject.DATA_FORMAT) -> {
                val name = db.getContent(ScoreObject.DATA_FORMAT) as String
                val obj = context[ScoreObjectRegistry].getOrNull(name)
                if (obj != null) {
                    item.target =
                        if (obj is ScoreObjectGroup) ItemTarget.Player(obj.reference())
                        else ItemTarget.Object(obj.reference())
                }
            }

            db.hasContent(BufferObject.DATA_FORMAT) -> {
                val buffer = db.getFrom(context[BufferRegistry], BufferObject.DATA_FORMAT) ?: return@drop
                createPlayBufTarget(ev, buffer, item)
            }

            db.hasContent(ScriptObject.DATA_FORMAT) -> {
                val script = db.getFrom(context.project.scripts, ScriptObject.DATA_FORMAT) ?: return@drop
                item.target = ItemTarget.Script(script.reference())
            }

            db.hasContent(LiveTaskObject.DATA_FORMAT) -> {
                val task = db.getFrom(context.project[LIVE_TASKS], LiveTaskObject.DATA_FORMAT) ?: return@drop
                item.target = ItemTarget.LiveTask(task.reference())
            }

            db.hasFile("wav") -> {
                val file = db.files[0]
                val buffer = context[BufferRegistry].getOrAdd(file)
                createPlayBufTarget(ev, buffer, item)
            }
        }
    }

    private fun createPlayBufTarget(ev: DragEvent, buffer: BufferObject, item: LauncherGrid.GridItem) {
        val synthDef = context.project[UI_STATE].getOrSelectInstrument(ev) ?: return
        val obj = buffer.createSynthObject(synthDef) ?: return
        context[ScoreObjectRegistry].add(obj)
        item.target = ItemTarget.Object(obj.reference())
    }

    private fun canDropOnItem(item: LauncherGrid.GridItem) = { db: Dragboard ->
        when {
            item.target.isActive.now -> false
            db.hasContent(PlaybackActions.RECORD_BUTTON) -> true
            db.hasContent(GridItemReference.DATA_FORMAT) -> {
                val ref = db.getContent(GridItemReference.DATA_FORMAT) as GridItemReference
                ref.getItem(grid) != item
            }

            db.hasContent(AudioFlow.DATA_FORMAT) -> true
            db.hasContent(ScoreObject.DATA_FORMAT) -> {
                val name = db.getContent(ScoreObject.DATA_FORMAT) as String
                val obj = context[ScoreObjectRegistry].getOrNull(name)
                obj != null && obj.affectsPlayback
            }

            db.hasContent(BufferObject.DATA_FORMAT) -> true
            db.hasContent(ScriptObject.DATA_FORMAT) -> true
            db.hasContent(LiveTaskObject.DATA_FORMAT) -> true
            db.hasFile("wav") -> true

            else -> false
        }
    }

    companion object: Type {
        override val uid: Int
            get() = 0

        override val defaultSide: Side
            get() = Side.TOP

        override val icon: Ikon
            get() = MaterialDesignG.GRID

        override val title: String
            get() = "Grid"

        override fun createToolPane(project: PonticelloProject): ToolPane = LauncherGridPane(project[LAUNCHER_GRID])

        private val detailsAction = detailsAction<LauncherGrid.GridItem>(
            applicability = { item -> item.target().map { target -> target.canStop } },
            sceneFill = DEFAULT_SCENE_FILL.opacity(0.5), labelWidth = 150.0
        ) { item ->
            CheckBox(item.stopOnRelease)
                .setupUndo(item.context[UndoManager], "Stop on release")
                .named("Stop on release")
        }

        private val itemActions = collectActions<ItemTarget> {
            addAction("View object") {
                applicableIf { target -> target.canView }
                icon(MaterialDesignE.EYE)
                executes { target ->
                    when (target) {
                        is ItemTarget.Flow -> {
                            val group = target.context[AudioFlows].getOrNull(target.ref.groupName) ?: return@executes
                            target.context[FlowGroupManager].showGroupPane(group)
                        }

                        is ItemTarget.Player -> {
                            val obj = target.ref.get() ?: return@executes
                            showObject(obj)
                        }

                        is ItemTarget.Object -> {
                            val obj = target.ref.get() ?: return@executes
                            showObject(obj)
                        }

                        is ItemTarget.Script -> {
                            val obj = target.ref.get() ?: return@executes
                            target.context[AppLayout].get<ScriptRegistryPane>().showContent(obj)
                        }

                        is ItemTarget.LiveTask -> {
                            val obj = target.ref.get() ?: return@executes
                            target.context[AppLayout].get<LiveTaskRegistryPane>().showContent(obj)
                        }

                        else -> {}
                    }
                }
            }
            addAction("Set velocity parameter") {
                applicableIf { target -> target is ItemTarget.Object && target.targetObject is ParameterizedObject }
                icon { target ->
                    if (target !is ItemTarget.Object || target.targetObject !is ParameterizedObject) reactiveValue(null)
                    else `if`(
                        target.velocityParameter.flatMap { p -> p.isResolved or (p.getName() == "level") },
                        then = { MaterialDesignA.ALPHA_V_BOX },
                        otherwise = { MaterialDesignA.ALPHA_V_BOX_OUTLINE }
                    )
                }
                toggleState { target ->
                    if (target !is ItemTarget.Object || target.targetObject !is ParameterizedObject) reactiveValue(false)
                    else target.velocityParameter.map { it != ObjectReference.none<ParameterDefObject>() }
                }
                executes { target, ev ->
                    val obj = target.targetObject as? ParameterizedObject ?: return@executes
                    when {
                        target !is ItemTarget.Object -> {}
                        ev is MouseEvent && ev.button == MouseButton.SECONDARY -> {
                            target.velocityParameter.set(ObjectReference.none())
                        }
//                        ev.isShiftDown() -> {
//                            val velocityParam = target.velocityParameter.now.get() ?: return@executes
//                            ControlSpecPrompt.create(velocityParam.name.now, obj, velocityParam.spec.now)
//                                ?.showDialog(ev)
//                        }
                        else -> {
                            val oldVelocityParam = target.velocityParameter.now
                            val newVelocityParam = SearchableParameterDefListView(
                                obj.def.allParameters(), "Select velocity parameter",
                                fixedParameterType = ParameterType.Numerical
                            ).showPopup(ev, initialOption = oldVelocityParam.get()) ?: return@executes
                            if (newVelocityParam != oldVelocityParam.get()) {
                                val ref = newVelocityParam.reference()
                                target.velocityParameter.set(ref)
                                target.context[UndoManager].record(
                                    VariableEdit(
                                        target.velocityParameter, oldVelocityParam, ref,
                                        "Select velocity parameter"
                                    )
                                )
                            }
                        }
                    }
                }
            }
            addAction("Configure recording") {
                enableWhen { target -> target.isActive.not() and (target is ItemTarget.ToggleRecording) }
                ifNotApplicable(Action.IfNotApplicable.Hide)
                icon(Codicons.SYMBOL_PROPERTY)
                executes { target, ev ->
                    PlaybackActions.selectRecordedBus(target.context[ScorePlayer.MAIN], ev)
                }
            }
            add(ScoreObjectRegistryPane.configureQuantizationAction) { target -> target.targetObject }
            add(ScoreObjectRegistryPane.quantizeStartAction) { target -> target.targetObject }
            add(ScoreObjectRegistryPane.toggleLoopingAction) { target ->
                target.targetObject.takeIf { target is ItemTarget.Player }
            }
        }

        private fun showObject(obj: @Contextual ScoreObject) {
            val objectsPane = obj.context[AppLayout].get<ScoreObjectRegistryPane>()
            objectsPane.showContent(obj)
        }

        private val actions = collectActions {
            addAll(UndoRedoActions) { grid: LauncherGrid -> grid.context[UndoManager] }
            addAction("Toggle active") {
                toggles(
                    LauncherGrid::isActive,
                    whenTrue = MaterialDesignR.RADIOBOX_MARKED,
                    whenFalse = MaterialDesignR.RADIOBOX_BLANK
                )
            }
        }
    }
}