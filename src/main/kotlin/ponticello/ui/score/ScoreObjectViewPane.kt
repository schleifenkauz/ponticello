package ponticello.ui.score

import fxutils.SubWindow
import fxutils.actions.Action
import fxutils.actions.ContextualizedAction
import fxutils.actions.collectActions
import fxutils.replace
import javafx.scene.Node
import javafx.scene.Parent
import javafx.scene.layout.Region
import org.kordamp.ikonli.Ikon
import org.kordamp.ikonli.materialdesign2.MaterialDesignP
import ponticello.model.live.LiveScoreObject
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

    private var playerPane: ScoreObjectPlayerPane? = null
        set(value) {
            field = value
            content = if (value == null) Region() else {
                value.borderPane.prefHeightProperty().bind(heightProperty().subtract(header.heightProperty()))
                value.borderPane
            }
        }

    override var content: Parent = Region()
        set(value) {
            if (children.size < 2) children.add(value)
            else children[1] = value
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

    fun showContent(obj: ScoreObject) {
        displayedObject.now = obj
        val pane = ScoreObjectPlayerPane.getPane(obj)
        playerPane = pane
        content.requestFocus()
        headerContent = pane.createToolbar()
        setShowing(true)
    }

    fun showContent(focusedView: ScoreObjectView) {
        val obj = focusedView.obj
        showContent(obj)
        playerPane!!.scorePane.positionInMainScore = focusedView::absolutePosition
    }

    fun showContent(liveObject: LiveScoreObject) {
        showContent(liveObject.scoreObject)
        playerPane!!.scorePane.quantization = liveObject.quantization
    }

    override fun onShowing() {
        val focused = context[ScoreObjectSelectionManager].focusedView.now ?: return
        showContent(focused)
    }

    override fun restoreShowing() {
    }

    private fun detach(obj: ScoreObject) {
        setShowing(false)
        displayedObject.now = null
        playerPane = null
        val title = windowTitle(obj)
        lateinit var newWindow: SubWindow
        val detachedToolPane = ScoreObjectViewPane()
        detachedToolPane.setup()
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
            get() = MaterialDesignP.PLAY_BOX_OUTLINE

        override val shortcut: String
            get() = "F7"

        override val defaultSide: Side
            get() = Side.BOTTOM

        override val supportedModes: List<ToolPaneMode>
            get() = listOf(ToolPaneMode.Docked, ToolPaneMode.Window)

        override fun createToolPane(project: PonticelloProject): ToolPane = ScoreObjectViewPane()
    }
}