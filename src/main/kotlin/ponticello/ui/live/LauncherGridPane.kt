package ponticello.ui.live

import fxutils.*
import fxutils.actions.*
import fxutils.controls.OptionSpinner
import fxutils.controls.SliderBar
import fxutils.drag.setupDropArea
import fxutils.prompt.SimpleSelectorPrompt
import fxutils.undo.UndoManager
import fxutils.undo.VariableEdit
import javafx.application.Platform
import javafx.geometry.Side.BOTTOM
import javafx.scene.Node
import javafx.scene.control.Button
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
import org.kordamp.ikonli.materialdesign2.*
import ponticello.impl.one
import ponticello.impl.toDecimal
import ponticello.impl.zero
import ponticello.model.instr.ParameterDefObject
import ponticello.model.instr.ParameterizedObject
import ponticello.model.live.*
import ponticello.model.live.LauncherGrid.GridItemReference
import ponticello.model.player.ScorePlayer
import ponticello.model.project.LAUNCHER_GRID
import ponticello.model.project.PonticelloProject
import ponticello.model.project.get
import ponticello.sc.NumericalControlSpec
import ponticello.sc.ParameterType
import ponticello.sc.Warp
import ponticello.ui.actions.PlaybackActions
import ponticello.ui.dock.AppLayout
import ponticello.ui.dock.Side
import ponticello.ui.dock.ToolPane
import ponticello.ui.dock.ToolPaneState
import ponticello.ui.registry.ParameterDefSelectorPrompt
import ponticello.ui.registry.ScriptRegistryPane
import ponticello.ui.score.FlowGroupManager
import ponticello.ui.score.ScoreObjectDetailPane
import ponticello.ui.score.ScoreObjectDuplicator
import ponticello.ui.score.ScoreObjectViewPane
import reaktive.value.binding.and
import reaktive.value.binding.impl.notNull
import reaktive.value.binding.map
import reaktive.value.binding.not
import reaktive.value.fx.asObservableValue
import reaktive.value.now
import reaktive.value.reactiveValue

class LauncherGridPane(
    private val grid: LauncherGrid,
) : ToolPane(), LauncherGrid.View {
    private val boxes = mutableMapOf<GridItem, Region>()
    override val content = GridPane().styleClass("launcher-grid-pane")
    private val bankSelectors = mutableListOf<Button>()
    private val bankSelectorsBox = HBox(5.0).centerChildren()
    private val addBankButton = button("+", style = "grid-bank-selector")
    override val headerContent: Node = setupHeader()
    override val type: Type get() = LauncherGridPane

    override val headerActions: List<ContextualizedAction> = actions.withContext(grid) + super.headerActions

    override fun defaultState(): ToolPaneState = ToolPaneState.window

    override fun afterSetup() {
        grid.addView(this)
        addBankButton.setOnAction { grid.addBank() }
        registerShortcuts {
            for (i in 1..9) {
                on("Ctrl+DIGIT$i") {
                    if (i - 1 in grid.availableBanks) {
                        grid.selectBank(i - 1)
                    }
                }
            }
            on("Ctrl+LEFT") {
                if (grid.currentBank != 0) {
                    grid.selectBank(grid.currentBank - 1)
                }
            }
            on("Ctrl+RIGHT") {
                if (grid.currentBank != grid.availableBanks.last) {
                    grid.selectBank(grid.currentBank + 1)
                }
            }
        }
    }

    private fun setupHeader(): HBox {
        val availableTargets = LauncherGrid.Target.options(grid.context)
        val targetSelector = SimpleSelectorPrompt(availableTargets, "Choose target")
            .selectorButton(grid.target, undoManager = grid.context[UndoManager])
        val headerContent = HBox(
            5.0,
            bankSelectorsBox,
            addBankButton,
            hspace(10.0),
            Label("Target: "), targetSelector,
        ).centerChildren()
        return headerContent
    }

    private fun setupGridPane() {
        content.children.clear()
        for (item in grid.items()) {
            val box = display(item)
            boxes[item] = box
            val (i, j) = grid.getGridIndices(item)
            content.add(box, j, i)
        }
    }

    override fun selectedBank(bank: Int) {
        for ((idx, selector) in bankSelectors.withIndex()) {
            selector.setPseudoClassState("selected", idx == bank)
        }
        setupGridPane()
    }

    override fun addedBank(bankIndex: Int) {
        val btn = Button().styleClass("grid-bank-selector")
        bankSelectors.add(bankIndex, btn)
        bankSelectorsBox.children.add(bankIndex, btn)
        btn.setOnMouseClicked { ev ->
            val bankIdx = bankSelectors.indexOf(btn)
            when (ev.button) {
                MouseButton.PRIMARY -> grid.selectBank(bankIdx)
                MouseButton.SECONDARY -> {
                    contextMenu(bankActions.withContext(Pair(grid, bankIdx))).show(btn, BOTTOM, 0.0, 0.0)
                }

                else -> {}
            }
            ev.consume()
        }
        updateBankButtonLabels(bankIndex)
    }

    override fun removedBank(bankIndex: Int) {
        bankSelectors.removeAt(bankIndex)
        bankSelectorsBox.children.removeAt(bankIndex)
        updateBankButtonLabels(bankIndex)
    }

    private fun updateBankButtonLabels(fromIndex: Int) {
        for ((idx, selector) in bankSelectors.withIndex().drop(fromIndex)) {
            selector.text = (idx + 1).toString()
        }
    }

    override fun updateItem(item: GridItem) = Platform.runLater {
        content.children.remove(boxes[item])
        val box = display(item)
        boxes[item] = box
        val (i, j) = grid.getGridIndices(item)
        content.add(box, j, i)
    }

    private fun display(item: GridItem): VBox {
        val target = item.target
        val buttonText = item.target.name
        val button = selectorButton(buttonText)
        button.setOnAction {
            val listView = SimpleSelectorPrompt(ItemTarget.options(grid.context), "Choose item target")
            val newTarget = listView.showPopup(button) ?: return@setOnAction
            item.target = newTarget
        }
        val actions = itemActions.withContext(target)
        val actionBar = ActionBar(actions, buttonStyle = "medium-icon-button")
        val centeredActionBar = HBox(infiniteSpace(), actionBar, infiniteSpace())
        val modeSpinner = OptionSpinner(item.mode, item.target.supportedModes)
        modeSpinner.disableProperty().bind(item.target.isActive.asObservableValue())
        modeSpinner.visibleProperty().bind(item.target().map { t -> t !is ItemTarget.None }.asObservableValue())
        modeSpinner.label.minWidth = 55.0
        val box = VBox(button, centeredActionBar, modeSpinner).styleClass("launcher-grid-item").centerChildren()
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

    private fun addMouseActions(box: VBox, item: GridItem) {
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

    private fun setupDragAndDrop(box: VBox, item: GridItem) {
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
                                    objectsPane.showContent(liveObject)
                                    val focusedView = objectsPane.playerPane?.scorePane?.getSingleObjectView()
                                    target.context[AppLayout].get<ScoreObjectDetailPane>().viewDetails(focusedView)
                                }
                            }
                        }

                        is ItemTarget.Object -> {
                            val obj = target.ref.get() ?: return@executes
                            val objectsPane = obj.context[AppLayout].get<ScoreObjectViewPane>()
                            objectsPane.showContent(obj)
                            val focusedView = objectsPane.playerPane?.scorePane?.getSingleObjectView()
                            obj.context[AppLayout].get<ScoreObjectDetailPane>().viewDetails(focusedView)
                        }

                        is ItemTarget.Script -> {
                            val obj = target.ref.get() ?: return@executes
                            target.context[AppLayout].get<ScriptRegistryPane>().showContent(obj)
                        }

                        else -> {}
                    }
                }
            }
            addAction("Add to score") {
                icon(MaterialDesignC.CONTENT_DUPLICATE)
                applicableIf { target -> target.targetObject != null }
                executes { target ->
                    val obj = target.targetObject!!
                    obj.context[ScoreObjectDuplicator].enterDuplicateMode(obj)
                }
            }
            addAction("Set velocity parameter") {
                applicableIf { target -> target is ItemTarget.Object && target.targetObject is ParameterizedObject }
                icon { target ->
                    if (target !is ItemTarget.Object || target.targetObject !is ParameterizedObject) reactiveValue(null)
                    else reactiveValue(MaterialDesignA.ALPHA_V_BOX)
                }
                toggleState { target ->
                    if (target !is ItemTarget.Object || target.targetObject !is ParameterizedObject) reactiveValue(false)
                    else target.velocityParameter.notNull()
                }
                executes { target, ev ->
                    val obj = target.targetObject as? ParameterizedObject ?: return@executes
                    when {
                        target !is ItemTarget.Object -> {}
                        ev is MouseEvent && ev.button == MouseButton.SECONDARY -> {
                            target.velocityParameter.set(null)
                        }
//                        ev.isShiftDown() -> {
//                            val velocityParam = target.velocityParameter.now.get() ?: return@executes
//                            ControlSpecPrompt.create(velocityParam.name.now, obj, velocityParam.spec.now)
//                                ?.showDialog(ev)
//                        }
                        else -> {
                            val oldVelocityParam = target.velocityParameter.now?.let { name ->
                                obj.getSpec(name)?.let { spec -> ParameterDefObject(name, spec) }
                            }
                            val newVelocityParam = ParameterDefSelectorPrompt(
                                obj.def.allParameters(), "Select velocity parameter",
                                fixedParameterType = ParameterType.Numerical
                            ).showPopup(ev, initialOption = oldVelocityParam) ?: return@executes
                            if (newVelocityParam != oldVelocityParam) {
                                val newParamName = newVelocityParam.name.now
                                target.velocityParameter.set(newParamName)
                                target.context[UndoManager].record(
                                    VariableEdit(
                                        target.velocityParameter, oldVelocityParam?.name?.now, newParamName,
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

        private val bankActions = collectActions<Pair<LauncherGrid, Int>> {
            addAction("Remove bank") {
                icon(MaterialDesignD.DELETE)
                executes { (grid, bankIndex) -> grid.removeBank(bankIndex) }
            }
        }
    }
}