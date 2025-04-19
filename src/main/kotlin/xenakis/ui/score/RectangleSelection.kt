package xenakis.ui.score

import javafx.scene.shape.Rectangle
import xenakis.impl.abs
import xenakis.impl.toDecimal
import xenakis.model.score.ObjectPosition
import xenakis.model.score.ScoreObject
import xenakis.model.score.ScoreObjectInstance

class RectangleSelection(private val pane: ScorePane, val rect: Rectangle, initialPosition: ObjectPosition) {
    private var corner1 = initialPosition
    private var corner2 = initialPosition

    val time get() = minOf(corner1.time, corner2.time)
    val y get() = minOf(corner1.y, corner2.y)
    val height get() = (corner1.y - corner2.y).abs()
    val duration get() = (corner1.time - corner2.time).abs()

    var isTimeSelection = false
        private set

    init {
        setOppositeCorner(corner2)
    }

    fun useAsTimeSelection() {
        isTimeSelection = true
        rect.y = 0.0
        rect.heightProperty().bind(pane.heightProperty().subtract(30))
    }

    fun setOppositeCorner(position: ObjectPosition) {
        corner2 = position
        rect.x = pane.getX(time)
        rect.width = pane.getWidth(duration)
        if (!isTimeSelection) {
            rect.y = pane.getScreenY(y)
            rect.height = pane.getScreenY(height)
        }
    }

    fun createInstance(obj: ScoreObject): ScoreObjectInstance {
        obj.setInitialSize(duration, height)
        return ScoreObjectInstance(obj, time, y)
    }

    fun isEmpty(): Boolean = (duration * height) < THRESHOLD

    companion object {
        private val THRESHOLD = 0.001.toDecimal()
    }
}