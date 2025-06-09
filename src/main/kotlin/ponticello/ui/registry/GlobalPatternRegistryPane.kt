package ponticello.ui.registry

import fxutils.SubWindow
import fxutils.actions.ContextualizedAction
import fxutils.actions.collectActions
import javafx.event.Event
import javafx.geometry.Pos
import javafx.scene.Parent
import javafx.scene.control.ScrollPane
import javafx.scene.input.DataFormat
import org.kordamp.ikonli.Ikon
import org.kordamp.ikonli.material2.Material2AL
import org.kordamp.ikonli.materialdesign2.MaterialDesignC
import org.kordamp.ikonli.materialdesign2.MaterialDesignL
import ponticello.model.obj.GlobalPatternObject
import ponticello.model.obj.SuperColliderObject
import ponticello.model.registry.ObjectRegistry
import ponticello.ui.dock.ToolPaneState
import ponticello.ui.impl.makeSubWindow
import ponticello.ui.misc.CodePane
import ponticello.ui.misc.PatternPlotPane
import reaktive.value.binding.map

class GlobalPatternRegistryPane(
    registry: ObjectRegistry<GlobalPatternObject>,
) : ObjectRegistryPane<GlobalPatternObject>(registry) {
    override val title: String
        get() = "Patterns"
    override val icon: Ikon
        get() = MaterialDesignL.LARAVEL

    override val supportedModes: Set<ObjectListView.DisplayMode>
        get() = ObjectListView.DisplayMode.all

    override val enableReordering: Boolean
        get() = true

    private val plotPaneWindows = mutableMapOf<GlobalPatternObject, SubWindow>()

    override fun defaultState(): ToolPaneState = ToolPaneState.docked(ToolPaneState.Side.RIGHT)

    override fun createNewObject(name: String, ev: Event?): GlobalPatternObject = GlobalPatternObject.create(name)

    override fun getContent(obj: GlobalPatternObject, mode: ObjectListView.DisplayMode): Parent = when (mode) {
        ObjectListView.DisplayMode.SubWindow -> {
            val actions = actions.withContext(listView.getBox(obj))
            CodePane(obj.patternCode, extraActions = actions, ownWindow = true, actionBarAlignment = Pos.BOTTOM_RIGHT)
        }

        else -> ScrollPane(obj.patternCode.control)
    }

    override fun configureSubWindow(window: SubWindow, obj: GlobalPatternObject) {
        window.width = 600.0
        window.height = 400.0
    }

    override fun detailWindowIcon(obj: GlobalPatternObject): Ikon = Material2AL.CODE

    override fun getActions(box: ObjectBox<GlobalPatternObject>): List<ContextualizedAction> =
        actions.withContext(box)

    override fun dataFormat(obj: GlobalPatternObject): DataFormat = GlobalPatternObject.DATA_FORMAT

    fun showPlotPane(obj: GlobalPatternObject) {
        val window = plotPaneWindows.getOrPut(obj) {
            val pane = PatternPlotPane(obj)
            val title = obj.name.map { n -> "Plot $n" }
            makeSubWindow(pane, title, obj.context)
        }
        window.showOrBringToFront()
    }

    companion object {
        private val actions = collectActions<ObjectBox<GlobalPatternObject>> {
            addAll(SuperColliderObject.actions) { box -> box.obj }
            addAction("Plot") {
                icon(MaterialDesignC.CHART_BOX_OUTLINE)
                shortcut("Alt+V")
                applicableIf { box -> box.config is GlobalPatternRegistryPane }
                executes { box ->
                    val pane = box.config as GlobalPatternRegistryPane
                    pane.showPlotPane(box.obj)
                }
            }
        }
    }
}