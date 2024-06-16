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

    override fun DetailPane.setupDetailPane() {
        addItem("Color:", colorPicker)
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
        val objects = obj.score.objects
        if (objects.isNotEmpty()) {
            minDur =
                if (cursor.resizeFromLeft) obj.duration - objects.minOf { o -> o.start }
                else objects.maxOf { o -> o.start + o.duration }

            minHeight =
                if (cursor.resizeFromTop) obj.height - objects.minOf { o -> o.y }
                else objects.maxOf { o -> o.y + o.height }
        }
        val deltaDur = dur.coerceAtLeast(minDur) - obj.duration
        val deltaHeight = height.coerceAtLeast(minHeight) - obj.height
        obj.duration += deltaDur
        obj.height += deltaHeight
        if (cursor.resizeFromLeft) {
            for (obj in obj.score.objects) {
                obj.position.start += deltaDur
            }
        }
        if (cursor.resizeFromTop) {
            for (obj in obj.score.objects) {
                obj.position.y += deltaHeight
            }
        }
    }
}