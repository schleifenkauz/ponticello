package ponticello.ui.registry

import fxutils.SubWindow
import fxutils.actions.ContextualizedAction
import fxutils.actions.collectActions
import fxutils.showAndBringToFront
import hextant.serial.EditorRoot
import javafx.event.Event
import javafx.scene.input.DataFormat
import org.kordamp.ikonli.Ikon
import org.kordamp.ikonli.materialdesign2.MaterialDesignC
import org.kordamp.ikonli.materialdesign2.MaterialDesignL
import ponticello.model.code.GlobalPatternObject
import ponticello.model.obj.SuperColliderObject
import ponticello.model.project.PATTERNS
import ponticello.model.project.PonticelloProject
import ponticello.model.project.patterns
import ponticello.model.registry.ObjectRegistry
import ponticello.sc.editor.ScExprEditor
import ponticello.ui.dock.ListToolPaneState
import ponticello.ui.dock.Side
import ponticello.ui.dock.ToolPane
import ponticello.ui.dock.ToolPaneState
import ponticello.ui.impl.makeSubWindow
import ponticello.ui.misc.PatternPlotPane
import ponticello.ui.registry.ObjectListView.DisplayMode
import reaktive.value.binding.map

class GlobalPatternRegistryPane(
    registry: ObjectRegistry<GlobalPatternObject>,
) : CodeObjectRegistryPane<GlobalPatternObject>(registry, PATTERNS.serializer) {
    override val type: Type
        get() = GlobalPatternRegistryPane

    override val canDuplicate: Boolean
        get() = true

    override val supportedModes: Set<DisplayMode>
        get() = setOf(DisplayMode.Collapsable, DisplayMode.Inline(false), DisplayMode.DetailsPane)

    private val plotPaneWindows = mutableMapOf<GlobalPatternObject, SubWindow>()

    override fun defaultState(): ToolPaneState = ListToolPaneState.docked

    override fun createNewObject(name: String, ev: Event?): GlobalPatternObject = GlobalPatternObject.create(name)

    override fun getEditorRoot(obj: GlobalPatternObject): EditorRoot<out ScExprEditor<*>> = obj.patternCode

    override fun configureSubWindow(window: SubWindow, obj: GlobalPatternObject) {
        window.width = 600.0
        window.height = 400.0
    }

    override fun getActions(box: ObjectBox<GlobalPatternObject>): List<ContextualizedAction> =
        actions.withContext(box)

    override val dataFormat: DataFormat
        get() = GlobalPatternObject.DATA_FORMAT

    fun showPlotPane(obj: GlobalPatternObject) {
        val window = plotPaneWindows.getOrPut(obj) {
            val pane = PatternPlotPane(obj)
            val title = obj.name.map { n -> "Plot $n" }
            makeSubWindow(pane, title, obj.context)
        }
        window.showAndBringToFront()
    }

    companion object : Type(9, "Patterns") {
        override val icon: Ikon
            get() = MaterialDesignL.LARAVEL

        override val defaultSide: Side
            get() = Side.RIGHT

        override fun createToolPane(project: PonticelloProject): ToolPane = GlobalPatternRegistryPane(project.patterns)

        private val actions = collectActions<ObjectBox<GlobalPatternObject>> {
            addAll(SuperColliderObject.actions) { box -> box.obj }
            addAction("Plot") {
                icon(MaterialDesignC.CHART_BOX_OUTLINE)
                applicableIf { box -> box.config is GlobalPatternRegistryPane }
                executes { box ->
                    val pane = box.config as GlobalPatternRegistryPane
                    pane.showPlotPane(box.obj)
                }
            }
        }
    }
}