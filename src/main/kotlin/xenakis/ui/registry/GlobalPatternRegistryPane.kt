package xenakis.ui.registry

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
import reaktive.value.binding.map
import xenakis.model.obj.GlobalPatternObject
import xenakis.model.obj.SuperColliderObject
import xenakis.model.registry.ObjectRegistry
import xenakis.ui.impl.makeSubWindow
import xenakis.ui.misc.CodePane
import xenakis.ui.misc.PatternPlotPane

class GlobalPatternRegistryPane(
    registry: ObjectRegistry<GlobalPatternObject>,
) : ObjectRegistryPane<GlobalPatternObject>(registry) {
    private val plotPaneWindows = mutableMapOf<GlobalPatternObject, SubWindow>()

    init {
        setup()
    }

    override fun createNewObject(name: String, ev: Event?): GlobalPatternObject = GlobalPatternObject.create(name)

    override val supportedModes: Set<ObjectListView.DisplayMode>
        get() = ObjectListView.DisplayMode.all

    override val enableReordering: Boolean
        get() = true

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