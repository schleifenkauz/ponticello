package xenakis.ui

import javafx.scene.Cursor
import javafx.scene.input.MouseEvent
import xenakis.model.CompoundScoreObject

class CompoundScoreObjectView(private val obj: CompoundScoreObject) : ScoreObjectView(obj) {
    lateinit var scorePane: ScorePane
        private set

    init {
        styleClass("sub-score")
    }

    override fun initialize(parent: ScorePane) {
        super.initialize(parent)
        obj.score.initialize(context)
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
    }

    override fun resizeObject(width: Double, height: Double, ev: MouseEvent, cursor: Cursor): Boolean {
        val dur = pane.getDuration(width)
        val deltaDur = obj.duration - dur
        val deltaHeight = obj.height - height
        val resizeFromLeft = cursor in setOf(Cursor.W_RESIZE, Cursor.NW_RESIZE, Cursor.SW_RESIZE)
        val resizeFromTop = cursor in setOf(Cursor.S_RESIZE, Cursor.SE_RESIZE, Cursor.SW_RESIZE)
        if (resizeFromLeft && obj.score.objects.any { o -> o.start < deltaDur }) return false
        if (resizeFromTop && obj.score.objects.any { o -> o.y < deltaHeight }) return false
        if (!resizeFromLeft && obj.score.objects.any { o -> o.start + o.duration > dur }) return false
        if (!resizeFromTop && obj.score.objects.any { o -> o.y + o.height > height }) return false
        obj.duration = dur
        obj.height = height
        return true
    }
}