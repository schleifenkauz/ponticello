package xenakis.ui.live

import bundles.createBundle
import fxutils.*
import fxutils.actions.action
import fxutils.actions.collectActions
import fxutils.actions.makeButton
import fxutils.controls.SliderBar
import hextant.context.Context
import javafx.scene.control.CheckBox
import javafx.scene.layout.GridPane
import javafx.scene.layout.HBox
import javafx.scene.layout.VBox
import org.kordamp.ikonli.materialdesign2.MaterialDesignE
import org.kordamp.ikonli.materialdesign2.MaterialDesignR
import reaktive.value.binding.flatMap
import reaktive.value.now
import reaktive.value.reactiveValue
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
) : ToolPane() {
    init {
        val gridPane = GridPane().styleClass("launcher-grid-pane")
        for (i in grid.rowIndices) {
            for (j in grid.columnIndices) {
                val item = grid[i, j]
                val box = display(item)
                gridPane.add(box, j, i)
            }
        }
        setup(gridPane, actions = actions.withContext(grid))
    }

    private fun display(item: LauncherGrid.GridItem): VBox {
        val selector = ScoreObjectSelector()
        selector.syncWith(item.ref)
        selector.initialize(context)
        val control = ObjectSelectorControl(selector, createBundle())
        val viewBtn = viewObjectAction.withContext(item).makeButton("medium-icon-button")
        val spec = NumericalControlSpec(zero, zero, one, 0.01.toDecimal(), zero, Warp.Linear)
        val name = reactiveValue("Y position")
        val scoreYSlider = SliderBar(item.yPosition, name, spec.converter()).pad(10.0)
        val freeOnReleaseOption = CheckBox("Stop on release").sync(item.freeOnRelease)
        return VBox(
            HBox(3.0, infiniteSpace(),  control, viewBtn, infiniteSpace()).centerChildren(),
            HBox(hspace(10.0), scoreYSlider),
            freeOnReleaseOption
        ).styleClass("launcher-grid-item")
    }

    companion object {
        private val viewObjectAction = action<LauncherGrid.GridItem>("View object") {
            icon(MaterialDesignE.EYE)
            applicableWhen { item -> item.ref.flatMap(ScoreObjectReference::isResolved) }
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