package ponticello.ui.score

import javafx.scene.shape.Rectangle
import ponticello.impl.abs
import ponticello.model.score.ObjectPosition
import ponticello.model.score.ScoreObject
import ponticello.model.score.ScoreObjectInstance

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
            val y1 = pane.getScreenY(corner1.y)
            val y2 = pane.getScreenY(corner2.y)
            rect.y = minOf(y1, y2)
            rect.height = maxOf(y1, y2) - minOf(y1, y2)
        }
    }

    fun createInstance(obj: ScoreObject): ScoreObjectInstance {
        obj.setInitialSize(duration, height)
        return ScoreObjectInstance(obj, time, y)
    }

    fun isEmpty(): Boolean = (rect.width * rect.height) < THRESHOLD

    companion object {
        private const val THRESHOLD = 5.0
    }
}