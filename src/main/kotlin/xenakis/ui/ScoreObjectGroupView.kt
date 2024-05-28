package xenakis.ui

import javafx.scene.Cursor
import javafx.scene.input.MouseEvent
import xenakis.model.ScoreObjectGroup

class ScoreObjectGroupView(private val obj: ScoreObjectGroup) : ScoreObjectView(obj) {
    lateinit var scorePane: ScorePane
        private set

    init {
        styleClass("sub-score")
    }

    override fun initialize(parent: ScorePane) {
        super.initialize(parent)
        scorePane = SubScorePane(obj, context, pane)
        children.add(scorePane)
        scorePane.prefWidthProperty().bind(widthProperty())
        scorePane.prefHeightProperty().bind(heightProperty())
        widthProperty().addListener { _ -> rescale() }
        heightProperty().addListener { _ -> rescale() }
        scorePane.repaint()
    }

    override fun rescale() {
        super.rescale()
        scorePane.repaint()
        for (view in scorePane.allViews) {
            view.rescale()
        }
    }

    override fun resizeObject(width: Double, height: Double, ev: MouseEvent, cursor: Cursor) {
        val dur = pane.getDuration(width)
        var minDur = 0.0
        var minHeight = 0.0
        val resizeFromLeft = cursor in setOf(Cursor.W_RESIZE, Cursor.NW_RESIZE, Cursor.SW_RESIZE)
        val resizeFromTop = cursor in setOf(Cursor.S_RESIZE, Cursor.SE_RESIZE, Cursor.SW_RESIZE)
        val objects = obj.score.objects
        if (objects.isNotEmpty()) {
            minDur =
                if (resizeFromLeft) objects.minOf { o -> obj.duration - o.start }
                else objects.maxOf { o -> o.start + o.duration }

            minHeight =
                if (resizeFromTop) objects.minOf { o -> obj.height - o.y }
                else objects.maxOf { o -> o.y + o.height }
        }
        obj.duration = dur.coerceAtLeast(minDur)
        obj.height = height.coerceAtLeast(minHeight)
    }
}