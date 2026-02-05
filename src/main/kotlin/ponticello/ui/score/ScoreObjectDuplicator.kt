package ponticello.ui.score

import bundles.PublicProperty
import bundles.publicProperty
import javafx.event.Event
import javafx.geometry.Rectangle2D
import javafx.scene.SnapshotParameters
import javafx.scene.image.Image
import javafx.scene.image.ImageView
import javafx.scene.robot.Robot
import ponticello.impl.Decimal
import ponticello.model.obj.project
import ponticello.model.project.UI_STATE
import ponticello.model.project.get
import ponticello.model.score.ObjectPosition
import ponticello.model.score.ScoreObject
import ponticello.model.score.ScoreObjectInstance
import ponticello.model.server.SampleObject
import ponticello.ui.launcher.PonticelloMainActivity
import reaktive.Observer

class ScoreObjectDuplicator { //TODO replace snapshots with deactivated ScoreObjectViews
    private val repaintObservers = mutableListOf<Observer>()

    var clipboardObject: ScoreObject? = null
        private set

    var isCloneMode: Boolean = false
        private set

    private val panes = mutableListOf<ScorePane>()
    private val imageViews = mutableMapOf<ScorePane, ImageView>()

    fun registerRootPane(pane: RootScorePane) {
        panes.add(pane)
        val observer = pane.onRepaint.observe { _ -> repainted(pane) }
        repaintObservers.add(observer)
    }

    fun enterDuplicateMode(obj: ScoreObject, view: ScoreObjectView, clone: Boolean) {
        val parameters = SnapshotParameters()
        view.snapshot(parameters, null)
        val image = view.snapshot(parameters, null)
        enterDuplicateMode(obj, image, view, clone)
    }

    fun enterDuplicateMode(obj: ScoreObject, clone: Boolean) {
        val inst = ScoreObjectInstance(obj, ObjectPosition.ZERO)
        val view = ScorePane.createObjectView(obj, inst)
        val mainScorePane = obj.context[PonticelloMainActivity].mainScoreView
        view.initialize(mainScorePane)
        view.setPrefSize(view.getDisplayWidth(), view.getDisplayHeight())
        view.rescale()
        enterDuplicateMode(obj, view, clone)
    }

    private fun enterDuplicateMode(obj: ScoreObject, image: Image, view: ScoreObjectView?, clone: Boolean) {
        if (clipboardObject != null) {
            exitDuplicateMode()
        }
        clipboardObject = obj
        isCloneMode = clone
        for (pane in panes) {
            val imageView = ImageView(image)
            imageView.opacity = 0.3
            imageView.viewOrder = -1000.0
            imageView.isMouseTransparent = true
            if (view != null) {
                imageView.viewport = Rectangle2D(10.0, 3.0, view.prefWidth - 4.0, view.prefHeight - 4.0)
            }
            resizeImageView(imageView, pane)
            imageView.visibleProperty().bind(pane.hoverProperty())
            pane.children.add(imageView)
            if (pane.isHover) {
                val mousePos = pane.screenToLocal(Robot().mousePosition)
                val (t, y) = pane.snapToGrid(mousePos.x, mousePos.y)
                imageView.relocate(pane.getX(t), pane.getScreenY(y))
            }
            imageViews[pane] = imageView
        }
    }

    fun enterDuplicateMode(sample: SampleObject, ev: Event?) {
        val image = Image(sample.spectrogramFile.inputStream())
        val instrument = sample.context.project[UI_STATE].getOrSelectInstrument(ev) ?: return
        val obj = sample.createSoundProcess(instrument) ?: return
        enterDuplicateMode(obj, image, null, clone = false)
    }

    private fun resizeImageView(view: ImageView, pane: ScorePane) {
        check(clipboardObject != null) { "Not in duplicate mode" }
        view.fitWidth = pane.getWidth(clipboardObject!!.duration) - 4.0
        view.fitHeight = pane.getScreenY(clipboardObject!!.height) - 4.0
    }

    private fun repainted(pane: ScorePane) {
        val imageView = imageViews[pane] ?: return
        check(clipboardObject != null) { "Not in duplicate mode" }
        imageView.fitWidth = pane.getWidth(clipboardObject!!.duration) - 4.0
        imageView.fitHeight = pane.getScreenY(clipboardObject!!.height) - 4.0
        if (imageView !in pane.children) pane.children.add(imageView)
    }

    fun movedCursor(pane: ScorePane, t: Decimal, y: Decimal) {
        val obj = clipboardObject ?: return
        val imageView = imageViews[pane] ?: return
        val layoutX = pane.getX(t.coerceAtMost(pane.score.maxTime - obj.duration))
        val layoutY = pane.getScreenY(y.coerceAtMost(pane.score.maxY - obj.height))
        imageView.relocate(layoutX, layoutY)
    }

    fun exitDuplicateMode() {
        clipboardObject = null
        for (pane in panes) {
            val imageView = imageViews.remove(pane) ?: continue
            pane.children.remove(imageView)
        }
    }

    fun isInDuplicateMode() = clipboardObject != null

    companion object : PublicProperty<ScoreObjectDuplicator> by publicProperty("ScoreObjectDuplicator")
}