package xenakis.ui.live

import fxutils.*
import fxutils.actions.Action
import fxutils.actions.ActionBar
import fxutils.actions.collectActions
import fxutils.controls.SliderBar
import fxutils.prompt.SimpleSearchableListView
import fxutils.undo.UndoManager
import fxutils.undo.VariableEdit
import javafx.scene.control.CheckBox
import javafx.scene.control.Label
import javafx.scene.input.DragEvent
import javafx.scene.input.Dragboard
import javafx.scene.input.MouseButton
import javafx.scene.input.TransferMode
import javafx.scene.layout.GridPane
import javafx.scene.layout.HBox
import javafx.scene.layout.Region
import javafx.scene.layout.VBox
import kotlinx.serialization.Contextual
import org.kordamp.ikonli.codicons.Codicons
import org.kordamp.ikonli.materialdesign2.MaterialDesignA
import org.kordamp.ikonli.materialdesign2.MaterialDesignE
import org.kordamp.ikonli.materialdesign2.MaterialDesignR
import reaktive.value.binding.and
import reaktive.value.binding.flatMap
import reaktive.value.binding.`if`
import reaktive.value.binding.not
import reaktive.value.now
import reaktive.value.reactiveValue
import xenakis.impl.one
import xenakis.impl.toDecimal
import xenakis.impl.zero
import xenakis.model.flow.AudioFlow
import xenakis.model.flow.AudioFlows
import xenakis.model.live.LauncherGrid
import xenakis.model.live.LauncherGrid.GridItemReference
import xenakis.model.live.LauncherGrid.ItemTarget
import xenakis.model.obj.ParameterDefReference
import xenakis.model.obj.ParameterizedObject
import xenakis.model.player.ScorePlayer
import xenakis.model.registry.ScoreObjectRegistry
import xenakis.model.registry.reference
import xenakis.model.score.ScoreObject
import xenakis.model.score.ScoreObjectGroup
import xenakis.sc.NumericalControlSpec
import xenakis.sc.Warp
import xenakis.ui.actions.PlaybackActions
import xenakis.ui.actions.UndoRedoActions
import xenakis.ui.launcher.XenakisMainActivity
import xenakis.ui.registry.ScoreObjectRegistryPane
import xenakis.ui.registry.SearchableParameterDefListView
import xenakis.ui.registry.ToolPane
import xenakis.ui.score.FlowGroupManager

class LauncherGridPane(
    private val grid: LauncherGrid,
) : ToolPane(), LauncherGrid.View {
    private val context = grid.context
    private val boxes = mutableMapOf<LauncherGrid.GridItem, Region>()
    private val gridPane = GridPane().styleClass("launcher-grid-pane")

    init {
        preparePlayers()
        setupGridPane()
        grid.addView(this)
        val headerContent = setupHeader()
        setup(gridPane, title = null, headerContent, actions = actions.withContext(grid))
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
            gridPane.add(box, j, i)
        }
    }

    override fun updateItem(item: LauncherGrid.GridItem) {
        gridPane.children.remove(boxes[item])
        val box = display(item)
        boxes[item] = box
        val (i, j) = grid.getGridIndices(item)
        gridPane.add(box, j, i)
    }

    private fun display(item: LauncherGrid.GridItem): VBox {
        val listView = SimpleSearchableListView(ItemTarget.options(context), "Choose item target")
        val target = item.target
        val buttonText = if (target is ItemTarget.None) "Select Target" else item.target.toString()
        val button = button(buttonText)
        button.setOnAction {
            val newTarget = listView.showPopup(button) ?: return@setOnAction
            item.target = newTarget
        }
        val actionBar = ActionBar(itemActions.withContext(target), buttonStyle = "medium-icon-button")
        val centeredActionBar = HBox(infiniteSpace(), actionBar, infiniteSpace())
        val box = VBox(button, centeredActionBar).styleClass("launcher-grid-item")
        if (target !is ItemTarget.None) {
            val freeOnReleaseOption = CheckBox().sync(item.stopOnRelease, "Stop on release", context[UndoManager])
            val optionBox = HBox(
                3.0,
                infiniteSpace(),
                Label("Stop on release: "), freeOnReleaseOption,
                infiniteSpace()
            ).centerChildren()
            box.children.add(optionBox)
        } else {
            box.centerChildren()
        }
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

    private fun dropOn(item: LauncherGrid.GridItem) = { ev: DragEvent ->
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
        }
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

            else -> false
        }
    }

    companion object {
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

                        else -> {}
                    }
                }
            }
            addAction("Set velocity parameter") {
                applicableIf { target -> target is ItemTarget.Object && target.targetObject is ParameterizedObject }
                icon { target ->
                    if (target !is ItemTarget.Object || target.targetObject !is ParameterizedObject) reactiveValue(null)
                    else `if`(
                        target.velocityParameter.flatMap(ParameterDefReference::isResolved),
                        then = { MaterialDesignA.ALPHA_V_BOX_OUTLINE },
                        otherwise = { MaterialDesignA.ALPHA_V_BOX }
                    )
                }
                executes { target, ev ->
                    if (target !is ItemTarget.Object) return@executes
                    val obj = target.targetObject as? ParameterizedObject ?: return@executes
                    val oldVelocityParam = target.velocityParameter.now
                    val newVelocityParam = SearchableParameterDefListView(
                        obj.def.allParameters(), "Select velocity parameter"
                    ).showPopup(ev, initialOption = oldVelocityParam.get()) ?: return@executes
                    if (newVelocityParam != oldVelocityParam.get()) {
                        val ref = newVelocityParam.reference()
                        target.velocityParameter.set(ref)
                        target.context[UndoManager].record(
                            VariableEdit(target.velocityParameter, oldVelocityParam, ref, "Select velocity parameter")
                        )
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
            val objectsPane = obj.context[XenakisMainActivity].scoreObjectsPane()
            objectsPane.listView.showContent(obj)
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