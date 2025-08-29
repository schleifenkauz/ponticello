package ponticello.ui.score

import fxutils.SubWindow
import fxutils.actions.Action
import fxutils.actions.ContextualizedAction
import fxutils.actions.collectActions
import fxutils.alwaysVGrow
import fxutils.replace
import javafx.scene.Node
import javafx.scene.Parent
import javafx.scene.layout.BorderPane
import javafx.scene.layout.Region
import org.kordamp.ikonli.Ikon
import org.kordamp.ikonli.materialdesign2.MaterialDesignM
import org.kordamp.ikonli.materialdesign2.MaterialDesignP
import ponticello.model.live.QuantizationConfig
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
    override val type: Type
        get() = ScoreObjectViewPane

    private val detached = mutableMapOf<ScoreObject, SubWindow>()
    private val displayedObject: ReactiveVariable<ScoreObject?> = reactiveVariable(null)

    override val title: ReactiveValue<String> =
        displayedObject.flatMap { obj -> obj?.name ?: reactiveValue("No object selected") }

    private var scorePane: SingleObjectScorePane? = null
        set(value) {
            field = value
            content = value?.let { BorderPane(value.alwaysVGrow()) } ?: Region()
        }

    override var content: Parent = Region()
        set(value) {
            children.replace(field, value)
            field = value
        }

    override var headerContent: Node = Region()
        set(value) {
            header.children.replace(field, value)
            field = value
        }

    override fun doSetup() {
    }

    override val headerActions: List<ContextualizedAction>
        get() = actions.withContext(this) + super.headerActions

    override fun defaultState(): ToolPaneState = ToolPaneState.docked

    fun showContent(focusedView: ScoreObjectView) {
        val obj = focusedView.obj
        showContent(obj)

    }

    fun showContent(obj: ScoreObject, quantization: QuantizationConfig? = null) {
        displayedObject.now = obj
        val pane = ScoreObjectPlayerPane.getPane(obj)
        content = pane.scorePane
        headerContent = pane.createToolbar()
        setShowing(true)
    }

    override fun onShowing() {
        val focused = context[ScoreObjectSelectionManager].focusedView.now ?: return
        showContent(focused)
    }

    private fun detach(obj: ScoreObject) {
        setShowing(false)
        displayedObject.now = null
        scorePane = null
        val title = windowTitle(obj)
        lateinit var newWindow: SubWindow
        val detachedToolPane = ScoreObjectViewPane()
        detachedToolPane.showContent(obj)
        newWindow = makeSubWindow(detachedToolPane, title, context)
        newWindow.show()
        detached[obj] = newWindow
    }

    private fun windowTitle(obj: ScoreObject) = obj.name.map { name -> "Object $name" }

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