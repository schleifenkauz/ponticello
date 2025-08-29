package ponticello.ui.live

import fxutils.*
import fxutils.actions.*
import fxutils.controls.CheckBox
import fxutils.controls.SliderBar
import fxutils.drag.setupDropArea
import fxutils.prompt.SimpleSearchableListView
import fxutils.undo.UndoManager
import fxutils.undo.VariableEdit
import javafx.scene.Node
import javafx.scene.control.Label
import javafx.scene.input.MouseButton
import javafx.scene.input.MouseEvent
import javafx.scene.input.TransferMode
import javafx.scene.layout.GridPane
import javafx.scene.layout.HBox
import javafx.scene.layout.Region
import javafx.scene.layout.VBox
import org.kordamp.ikonli.Ikon
import org.kordamp.ikonli.codicons.Codicons
import org.kordamp.ikonli.materialdesign2.MaterialDesignA
import org.kordamp.ikonli.materialdesign2.MaterialDesignE
import org.kordamp.ikonli.materialdesign2.MaterialDesignG
import org.kordamp.ikonli.materialdesign2.MaterialDesignR
import ponticello.impl.one
import ponticello.impl.toDecimal
import ponticello.impl.zero
import ponticello.model.live.ItemTarget
import ponticello.model.live.LauncherGrid
import ponticello.model.live.LauncherGrid.GridItemReference
import ponticello.model.live.LiveScoreObject
import ponticello.model.live.LiveTaskObject
import ponticello.model.obj.ParameterDefObject
import ponticello.model.obj.ParameterizedObject
import ponticello.model.player.ScorePlayer
import ponticello.model.project.LAUNCHER_GRID
import ponticello.model.project.PonticelloProject
import ponticello.model.project.get
import ponticello.model.registry.ObjectReference
import ponticello.model.registry.reference
import ponticello.sc.NumericalControlSpec
import ponticello.sc.ParameterType
import ponticello.sc.Warp
import ponticello.ui.actions.PlaybackActions
import ponticello.ui.dock.AppLayout
import ponticello.ui.dock.Side
import ponticello.ui.dock.ToolPane
import ponticello.ui.dock.ToolPaneState
import ponticello.ui.impl.DEFAULT_SCENE_FILL
import ponticello.ui.registry.ScriptRegistryPane
import ponticello.ui.registry.SearchableParameterDefListView
import ponticello.ui.score.FlowGroupManager
import ponticello.ui.score.ScoreObjectViewPane
import reaktive.value.binding.*
import reaktive.value.now
import reaktive.value.reactiveValue

class LauncherGridPane(
    private val grid: LauncherGrid,
) : ToolPane(), LauncherGrid.View {
    private val boxes = mutableMapOf<LauncherGrid.GridItem, Region>()
    override val content = GridPane().styleClass("launcher-grid-pane")
    override val headerContent: Node = setupHeader()
    override val type: Type get() = LauncherGridPane

    override val headerActions: List<ContextualizedAction> = actions.withContext(grid) + super.headerActions

    override fun defaultState(): ToolPaneState = ToolPaneState.window

    override fun afterSetup() {
        setupGridPane()
        grid.addView(this)
    }

    private fun setupHeader(): HBox {
        val availableTargets = LauncherGrid.Target.options(grid.context)
        val targetSelector = SimpleSearchableListView(availableTargets, "Choose target")
            .selectorButton(grid.target, undoManager = grid.context[UndoManager])
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
            val listView = SimpleSearchableListView(ItemTarget.options(grid.context), "Choose item target")
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
                    target.yPosition, "Score Y", spec.converter(),
                    undoManager = grid.context[UndoManager],
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
                item.target = ItemTarget.None()
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
        box.setupDropArea(LauncherGridItemDropHandler(grid, item))
    }

    companion object : Type(0, "Grid") {
        override val defaultSide: Side
            get() = Side.TOP

        override val icon: Ikon
            get() = MaterialDesignG.GRID

        override val shortcut: String
            get() = "Ctrl+G"

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
                            val group = target.ref.get()?.parentGroup ?: return@executes
                            target.context[FlowGroupManager].showFlowGroup(group)
                        }

                        is ItemTarget.LiveObjectRef -> {
                            val liveObject = target.liveObject ?: return@executes
                            when (liveObject) {
                                is LiveTaskObject ->
                                    target.context[AppLayout].get<LiveObjectRegistryPane>().showContent(liveObject)
                                is LiveScoreObject -> {
                                    val objectsPane = target.context[AppLayout].get<ScoreObjectViewPane>()
                                    objectsPane.showContent(liveObject.scoreObject, liveObject.quantization)
                                }
                            }
                        }

                        is ItemTarget.Object -> {
                            val obj = target.ref.get() ?: return@executes
                            val objectsPane = obj.context[AppLayout].get<ScoreObjectViewPane>()
                            objectsPane.showContent(obj) //TODO choose quantization
                        }

                        is ItemTarget.Script -> {
                            val obj = target.ref.get() ?: return@executes
                            target.context[AppLayout].get<ScriptRegistryPane>().showContent(obj)
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
                                target.context, fixedParameterType = ParameterType.Numerical
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
            add(LiveObjectRegistryPane.configureQuantizationAction) { target ->
                (target as? ItemTarget.LiveObjectRef)?.liveObject as? LiveScoreObject
            }
            add(LiveObjectRegistryPane.toggleLoopingAction) { target ->
                (target as? ItemTarget.LiveObjectRef)?.liveObject as? LiveScoreObject
            }
        }

        private val actions = collectActions {
//            addAll(UndoRedoActions) { grid: LauncherGrid -> grid.context[UndoManager] }
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