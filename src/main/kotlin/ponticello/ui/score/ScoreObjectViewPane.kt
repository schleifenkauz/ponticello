package ponticello.ui.score

import fxutils.SubWindow
import fxutils.actions.Action
import fxutils.actions.ContextualizedAction
import fxutils.actions.collectActions
import fxutils.replace
import javafx.scene.Parent
import javafx.scene.layout.Priority
import javafx.scene.layout.Region
import org.kordamp.ikonli.Ikon
import org.kordamp.ikonli.materialdesign2.MaterialDesignM
import org.kordamp.ikonli.materialdesign2.MaterialDesignP
import ponticello.model.project.PonticelloProject
import ponticello.model.score.ScoreObject
import ponticello.ui.dock.Side
import ponticello.ui.dock.ToolPane
import ponticello.ui.dock.ToolPaneMode
import ponticello.ui.dock.ToolPaneState
import ponticello.ui.impl.makeSubWindow
import reaktive.value.*
import reaktive.value.binding.flatMap
import reaktive.value.binding.impl.notNull
import reaktive.value.binding.map

class ScoreObjectViewPane : ToolPane() {
    private val detached = mutableMapOf<ScoreObject, SubWindow>()
    private val displayedObject: ReactiveVariable<ScoreObjectView?> = reactiveVariable(null)

    override val type: Type
        get() = ScoreObjectViewPane

    override val title: ReactiveValue<String> = displayedObject.flatMap { v -> v?.obj?.name ?: reactiveValue("No object selected") }

    override var content: Parent = noSelectedObject()
        set(value) {
            children.replace(field, value)
            field = value
            setVgrow(value, Priority.ALWAYS)
        }

    override fun doSetup() {
    }

    override val headerActions: List<ContextualizedAction>
        get() = actions.withContext(this) + super.headerActions

    override fun defaultState(): ToolPaneState = ToolPaneState.docked

    private fun noSelectedObject() = Region()

    fun showContent(focusedView: ScoreObjectView) {
        displayedObject.now = focusedView
        showObject(focusedView)
    }

    private fun showObject(view: ScoreObjectView) {
        val pane = ScoreObjectPlayerPane.getPane(view.obj)
        content = pane
    }

    override fun onShowing() {
        val focused = context[ScoreObjectSelectionManager].focusedView.now ?: return
        showContent(focused)
    }

    private fun detach(view: ScoreObjectView) {
        setShowing(false)
        displayedObject.now = null
        content = noSelectedObject()
        val title = windowTitle(view)
        lateinit var newWindow: SubWindow
        val pane = ScoreObjectPlayerPane.getPane(view.obj)
        newWindow = makeSubWindow(pane, title, context)
        newWindow.show()
        detached[view.obj] = newWindow
    }

    private fun windowTitle(view: ScoreObjectView) = view.obj.name.map { name -> "Object $name" }

    companion object : Type(uid = 16, "Object view") {
        private val actions = collectActions<ScoreObjectViewPane> {
            addAction("Detach") {
                icon(MaterialDesignP.PIN_OUTLINE)
                shortcuts("Ctrl+D")
                enableWhen { pane -> pane.displayedObject.notNull() }
                ifNotApplicable(Action.IfNotApplicable.Hide)
                executes { pane ->
                    val view = pane.displayedObject.now ?: return@executes
                    pane.detach(view)
                }
            }
        }

        override val icon: Ikon
            get() = MaterialDesignM.MAGNIFY

        override val shortcut: String
            get() = "F7"

        override val defaultSide: Side
            get() = Side.BOTTOM

        override val supportedModes: List<ToolPaneMode>
            get() = listOf(ToolPaneMode.Docked, ToolPaneMode.Window)

        override fun createToolPane(project: PonticelloProject): ToolPane = ScoreObjectViewPane()
    }
}