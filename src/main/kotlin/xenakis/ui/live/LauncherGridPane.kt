package xenakis.ui.live

import bundles.createBundle
import fxutils.*
import fxutils.actions.Action
import fxutils.actions.action
import fxutils.actions.collectActions
import fxutils.actions.makeButton
import fxutils.controls.SliderBar
import fxutils.prompt.SimpleSearchableListView
import hextant.context.Context
import javafx.scene.control.CheckBox
import javafx.scene.control.Label
import javafx.scene.layout.GridPane
import javafx.scene.layout.HBox
import javafx.scene.layout.Region
import javafx.scene.layout.VBox
import org.kordamp.ikonli.materialdesign2.MaterialDesignE
import org.kordamp.ikonli.materialdesign2.MaterialDesignR
import reaktive.value.binding.flatMap
import reaktive.value.now
import xenakis.impl.one
import xenakis.impl.toDecimal
import xenakis.impl.zero
import xenakis.model.live.LauncherGrid
import xenakis.model.obj.ScoreObjectReference
import xenakis.sc.NumericalControlSpec
import xenakis.sc.Warp
import xenakis.sc.editor.ScoreObjectSelector
import xenakis.sc.view.ObjectSelectorControl
import xenakis.ui.launcher.XenakisMainActivity
import xenakis.ui.registry.ToolPane

class LauncherGridPane(
    private val context: Context,
    private val grid: LauncherGrid,
) : ToolPane(), LauncherGrid.Listener {
    private val boxes = mutableListOf<Region>()

    init {
        grid.listeners.addListener(this)
        val headerContent = setupHeader()
        val gridPane = setupGridPane()
        setup(gridPane, title = null, headerContent, actions = actions.withContext(grid))
    }

    private fun setupHeader(): HBox {
        val availableTargets = LauncherGrid.Target.options(context)
        val targetSelector = SimpleSearchableListView(availableTargets, "Choose target")
            .selectorButton(grid.target)
        val headerContent = HBox(5.0, Label("Target: "), targetSelector).centerChildren()
        return headerContent
    }

    private fun setupGridPane(): GridPane {
        val gridPane = GridPane().styleClass("launcher-grid-pane")
        for (i in grid.rowIndices) {
            for (j in grid.columnIndices) {
                val item = grid[i, j]
                val box = display(item)
                val index = grid.getIndex(i, j)
                box.setOnMousePressed { ev ->
                    if (ev.target == box) grid.noteOn(index, velocity = 64)
                }
                box.setOnMouseReleased { ev ->
                    if (ev.target == box) grid.noteOff(index)
                }
                gridPane.add(box, j, i)
                boxes.add(box)
            }
        }
        return gridPane
    }

    private fun display(item: LauncherGrid.GridItem): VBox {
        val selector = ScoreObjectSelector()
        selector.syncWith(item.ref)
        selector.initialize(context)
        val control = ObjectSelectorControl(selector, createBundle())
        val viewBtn = viewObjectAction.withContext(item).makeButton("medium-icon-button")
        val spec = NumericalControlSpec(zero, zero, one, 0.01.toDecimal(), zero, Warp.Linear)
        val scoreYSlider = SliderBar(item.yPosition, "Score Y", spec.converter())
            .setFixedWidth(120.0)
            .pad(10.0)
        val freeOnReleaseOption = CheckBox("Stop on release").sync(item.freeOnRelease)
        return VBox(
            HBox(3.0, infiniteSpace(), control, viewBtn, infiniteSpace()).centerChildren(),
            HBox(hspace(10.0), scoreYSlider),
            freeOnReleaseOption
        ).styleClass("launcher-grid-item")
    }

    override fun pressed(index: Int) {
        boxes[index].setPseudoClassState("pressed", true)
    }

    override fun released(index: Int) {
        boxes[index].setPseudoClassState("pressed", false)
    }

    companion object {
        private val viewObjectAction = action<LauncherGrid.GridItem>("View object") {
            icon(MaterialDesignE.EYE)
            applicableWhen { item -> item.ref.flatMap(ScoreObjectReference::isResolved) }
            ifNotApplicable(Action.IfNotApplicable.Disable)
            executes { item ->
                val obj = item.ref.now.get() ?: return@executes
                val objectsPane = obj.context[XenakisMainActivity].scoreObjectsPane
                objectsPane.listView.showContent(obj)
            }
        }

        private val actions = collectActions {
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