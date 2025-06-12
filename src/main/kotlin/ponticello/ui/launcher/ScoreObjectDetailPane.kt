package ponticello.ui.launcher

import bundles.PublicProperty
import bundles.publicProperty
import bundles.set
import fxutils.SubWindow
import fxutils.actions.action
import fxutils.actions.registerShortcuts
import fxutils.label
import fxutils.opacity
import fxutils.replace
import hextant.context.Context
import javafx.scene.Parent
import javafx.scene.layout.BorderPane
import javafx.scene.layout.Priority
import javafx.scene.layout.StackPane
import javafx.stage.Popup
import javafx.stage.Screen
import org.kordamp.ikonli.Ikon
import org.kordamp.ikonli.materialdesign2.MaterialDesignP
import org.kordamp.ikonli.materialdesign2.MaterialDesignT
import ponticello.model.project.PonticelloProject
import ponticello.model.score.ScoreObject
import ponticello.ui.actions.ArrowKeys
import ponticello.ui.actions.ObjectActionContext
import ponticello.ui.actions.ObjectActions
import ponticello.ui.dock.Side
import ponticello.ui.dock.ToolPane
import ponticello.ui.dock.ToolPaneState
import ponticello.ui.impl.DEFAULT_SCENE_FILL
import ponticello.ui.impl.makeSubWindow
import ponticello.ui.impl.sceneFill
import ponticello.ui.score.ScoreObjectSelectionManager
import ponticello.ui.score.ScoreObjectView
import reaktive.Observer
import reaktive.value.binding.map

class ScoreObjectDetailPane(private val context: Context) : ToolPane() {
    private val detached = mutableMapOf<ScoreObject, SubWindow>()
    private var displayedObject: ScoreObjectView? = null
    private val focusedViewObserver: Observer

    override val type: Type
        get() = ScoreObjectDetailPane
    override var content: Parent = noSelectedObject()
        set(value) {
            children.replace(field, value)
            field = value
            setVgrow(value, Priority.ALWAYS)
        }

    init {
        setup()
        val selector = context[ScoreObjectSelectionManager]
        focusedViewObserver = selector.focusedView.observe { _, _, focused ->
            if (window !is Popup) {
                updateContent(focused)
            }
        }
        context[ScoreObjectDetailPane] = this
    }

    override fun defaultState(): ToolPaneState = ToolPaneState.docked

    private fun noSelectedObject() = BorderPane(label("No object selected"))

    fun updateContent(focusedView: ScoreObjectView?) {
        displayedObject = focusedView
        if (focusedView == null) {
            content = noSelectedObject()
            if (window is Popup) {
                setShowing(false)
            }
            return
        }
        showDetailPaneFor(focusedView)
    }

    fun hidePopup() {
        if (window is Popup) {
            setShowing(false)
        }
    }

    override fun makePopup(): Popup {
        val popup = super.makePopup()
        popup.isAutoFix = true
        popup.sizeToScene()
        popup.sceneFill(DEFAULT_SCENE_FILL.opacity(0.5))
        ArrowKeys.registerArrowKeys(popup.scene, context)
        return popup
    }

    override fun showPopup(popup: Popup): Boolean {
        val view = displayedObject ?: return false
        val titleBarHeight = 0.0
        val boundsInScreen = view.localToScreen(view.boundsInLocal) ?: return false
        val screen = Screen.getScreensForRectangle(boundsInScreen.centerX, boundsInScreen.centerY, 1.0, 1.0)
            .firstOrNull() ?: return false
        val screenCenterY = screen.visualBounds.minY + screen.visualBounds.height / 2.0
        val x = boundsInScreen.minX.coerceIn(screen.visualBounds.minX, screen.visualBounds.maxX)
        val y: Double
        val prefHeight = popup.scene.root.prefHeight(-1.0)
        if (boundsInScreen.centerY > screenCenterY) {
            popup.height = (prefHeight + titleBarHeight).coerceAtMost(boundsInScreen.minY - screen.bounds.minY)
            y = boundsInScreen.minY - popup.height
        } else {
            y = boundsInScreen.maxY
            popup.height = (prefHeight + titleBarHeight).coerceAtMost(screen.bounds.maxY - y)
        }
        popup.show(view, x, y)
        return true
    }

    private fun showDetailPaneFor(view: ScoreObjectView) {
        val detachAction = action<Unit>("Detach") {
            icon(MaterialDesignP.PIN_OUTLINE)
            shortcuts("Ctrl+D")
            executes { _ ->
                window?.hide()
                detach(view)
            }
        }.withContext(Unit)
        val detailPane = view.getDetailPane(listOf(detachAction))
        val pane = StackPane(detailPane)
        addActions(view, pane)
        content = pane
    }

    private fun detach(view: ScoreObjectView) {
        val title = windowTitle(view)
        lateinit var newWindow: SubWindow
        val detailPane = view.getDetailPane()
        val pane = StackPane(detailPane)
        newWindow = makeSubWindow(pane, title, context)
        addActions(view, pane)
        newWindow.show()
        detached[view.obj] = newWindow
    }

    private fun addActions(view: ScoreObjectView, pane: StackPane) {
        val ctx = ObjectActionContext.SingleObjectContext(view)
        val actions = ObjectActions.singleObjectActions.withContext(ctx)
        pane.registerShortcuts(actions)
    }

    private fun windowTitle(view: ScoreObjectView) = view.obj.name.map { name -> "Object $name" }

    companion object : PublicProperty<ScoreObjectDetailPane> by publicProperty("DetailPaneManager"),
        Type(5, "Score Object Details") {

        override val icon: Ikon
            get() = MaterialDesignT.TUNE_VARIANT

        override val shortcuts: Array<String>
            get() = arrayOf("F1")

        override val defaultSide: Side
            get() = Side.LEFT

        override fun createToolPane(project: PonticelloProject): ToolPane = ScoreObjectDetailPane(project.context)

        private const val TITLE_BAR_HEIGHT = 35.0
    }
}