package ponticello.ui.live

import fxutils.*
import fxutils.actions.Action
import fxutils.actions.ActionBar
import fxutils.actions.collectActions
import fxutils.controls.OptionSpinner
import fxutils.controls.SliderBar
import fxutils.drag.setupDropArea
import fxutils.prompt.PromptPlacement
import fxutils.prompt.nextToTarget
import fxutils.undo.UndoManager
import javafx.application.Platform
import javafx.scene.Node
import javafx.scene.control.Label
import javafx.scene.control.Tooltip
import javafx.scene.input.MouseButton
import javafx.scene.input.TransferMode
import javafx.scene.layout.*
import org.kordamp.ikonli.codicons.Codicons
import org.kordamp.ikonli.materialdesign2.MaterialDesignC
import org.kordamp.ikonli.materialdesign2.MaterialDesignE
import ponticello.impl.one
import ponticello.impl.toDecimal
import ponticello.impl.zero
import ponticello.model.live.GridItem
import ponticello.model.live.ItemTarget
import ponticello.model.live.LiveScoreObject
import ponticello.model.live.LiveTaskObject
import ponticello.model.midi.MidiGridInstrument
import ponticello.model.midi.MidiGridInstrument.GridItemReference
import ponticello.model.player.ScorePlayer
import ponticello.sc.NumericalControlSpec
import ponticello.sc.Warp
import ponticello.ui.actions.PlaybackActions
import ponticello.ui.dock.AppLayout
import ponticello.ui.registry.ScriptRegistryPane
import ponticello.ui.score.FlowGroupManager
import ponticello.ui.score.ScoreObjectDetailPane
import ponticello.ui.score.ScoreObjectDuplicator
import ponticello.ui.score.ScoreObjectViewPane
import reaktive.value.binding.and
import reaktive.value.binding.map
import reaktive.value.binding.not
import reaktive.value.fx.asObservableValue
import reaktive.value.reactiveValue

class MidiGridPane(private val grid: MidiGridInstrument) : MidiGridInstrument.View, GridPane() {
    private val boxes = mutableMapOf<GridItem, Region>()

    init {
        styleClass("launcher-grid-pane")
        grid.addView(this)
        setupBankSelectorShortcuts()
    }

    private fun setupBankSelectorShortcuts() {
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

    override fun selectedBank(bank: Int) {
        this.children.clear()
        for (item in grid.items()) {
            val box = display(item)
            boxes[item] = box
            val (i, j) = grid.getGridIndices(item)
            add(box, j, (grid.rows - 1) - i)
        }
    }

    override fun updateItem(item: GridItem) = Platform.runLater {
        children.remove(boxes[item])
        val box = display(item)
        boxes[item] = box
        val (i, j) = grid.getGridIndices(item)
        add(box, j, (grid.rows - 1) - i)
    }

    private fun display(item: GridItem): Region {
        val target = item.target
        val label = Label() styleClass "grid-item-label"
        label.textProperty().bind(item.target.name.asObservableValue())
        val centeredLabel = HBox(infiniteSpace(), label, infiniteSpace())

        val actions = itemActions.withContext(target)
        val actionBar = ActionBar(actions, buttonStyle = "small-icon-button")
        val centeredActionBar = HBox(infiniteSpace(), actionBar, infiniteSpace())

        val modeSpinner = OptionSpinner(item.mode, item.target.supportedModes)
        modeSpinner.disableProperty().bind(item.isActive.asObservableValue())
        modeSpinner.visibleProperty().bind(item.target().map { t -> t !is ItemTarget.None }.asObservableValue())
        modeSpinner.label.minWidth = 45.0

        val box = BorderPane().styleClass("midi-grid-item")
        box.top = centeredLabel
        box.center = centeredActionBar
        box.bottom = modeSpinner
        if (target is ItemTarget.Object) {
            val obj = target.ref.get()
            if (obj != null) {
                val spec = NumericalControlSpec(zero, zero, one, 0.01.toDecimal(), Warp.Linear, zero)
                val scoreYSlider = SliderBar(
                    target.yPosition, "Score Y", spec.converter(),
                    undoManager = grid.context[UndoManager],
                )
                scoreYSlider.maxWidth = 50.0
                box.bottom = VBox(modeSpinner, scoreYSlider)
            }
        }
        val tooltip = Tooltip()
        tooltip.textProperty().bind(item.target.name.asObservableValue())
        Tooltip.install(box, tooltip)
        addMouseActions(box, item)
        setupDragAndDrop(box, item)
        box.userData = box.bindPseudoClassState("active", item.isActive)
        return box
    }

    private fun addMouseActions(target: Node, item: GridItem) {
        target.setOnMousePressed { ev ->
            if (ev.button == MouseButton.PRIMARY) {
                grid.noteOn(item)
            }
        }
        target.setOnMouseClicked { ev ->
            if (ev.target == target && ev.button == MouseButton.SECONDARY) {
                item.target = ItemTarget.None()
            }
        }
        target.setOnMouseReleased { ev ->
            if (ev.button == MouseButton.PRIMARY) {
                grid.noteOff(item)
            }
        }
    }

    private fun setupDragAndDrop(cell: Region, item: GridItem) {
        cell.setOnDragDetected { ev ->
            if (ev.modifiers != setOf(Ctrl)) return@setOnDragDetected
            val db = cell.startDragAndDrop(TransferMode.MOVE)
            val ref = item.reference()
            db.setContent(mapOf(GridItemReference.DATA_FORMAT to ref))
            ev.consume()
        }
        cell.setupDropArea(MidiGridItemDropHandler(grid, item))
    }

    companion object {
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
                            val focusedView = objectsPane.playerPane?.scorePane?.getSingleObjectView()
                            obj.context[AppLayout].get<ScoreObjectDetailPane>().viewDetails(focusedView)
                            if (focusedView != null) {
                                objectsPane.showContent(focusedView)
                            } else {
                                objectsPane.showContent(obj)
                            }
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
            addAction("Configure recording") {
                enableWhen { target ->
                    (target.isActive?.not() ?: reactiveValue(false)) and (target is ItemTarget.ToggleRecording)
                }
                ifNotApplicable(Action.IfNotApplicable.Hide)
                icon(Codicons.SYMBOL_PROPERTY)
                executes { target, ev ->
                    val promptPlacement = ev?.nextToTarget() ?: PromptPlacement.Centered()
                    PlaybackActions.selectRecordedBus(target.context[ScorePlayer.MAIN], promptPlacement)
                }
            }
            add(LiveObjectRegistryPane.configureQuantizationAction) { target ->
                (target as? ItemTarget.LiveObjectRef)?.liveObject as? LiveScoreObject
            }
            add(LiveObjectRegistryPane.toggleLoopingAction) { target ->
                (target as? ItemTarget.LiveObjectRef)?.liveObject as? LiveScoreObject
            }
        }
    }
}