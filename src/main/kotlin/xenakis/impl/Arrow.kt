package xenakis.impl

import javafx.scene.paint.Color
import javafx.scene.shape.LineTo
import javafx.scene.shape.MoveTo
import javafx.scene.shape.Path
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 *
 * @author kn
 */
class Arrow(
    private var startX: Double, private var startY: Double,
    private var endX: Double, private var endY: Double,
    private val arrowHeadSize: Double = DEFAULT_ARROW_HEAD_SIZE
) : Path() {
    constructor() : this(0.0, 0.0, 0.0, 0.0)

    init {
        strokeProperty().bind(fillProperty())
        fill = Color.BLACK
        draw()
    }

    val start get() = Point(startX, startY)
    val end get() = Point(endX, endY)

    fun setStart(x: Double, y: Double) {
        startX = x
        startY = y
        draw()
    }

    fun setEnd(x: Double, y: Double) {
        endX = x
        endY = y
        draw()
    }

    private fun draw() {
        elements.clear()
        //Line
        elements.add(MoveTo(startX, startY))
        elements.add(LineTo(endX, endY))

        //ArrowHead
        val angle = atan2((endY - startY), (endX - startX)) - Math.PI / 2.0
        val sin = sin(angle)
        val cos = cos(angle)
        //point1
        val x1 = (-1.0 / 2.0 * cos + sqrt(3.0) / 2 * sin) * arrowHeadSize + endX
        val y1 = (-1.0 / 2.0 * sin - sqrt(3.0) / 2 * cos) * arrowHeadSize + endY
        //point2
        val x2 = (1.0 / 2.0 * cos + sqrt(3.0) / 2 * sin) * arrowHeadSize + endX
        val y2 = (1.0 / 2.0 * sin - sqrt(3.0) / 2 * cos) * arrowHeadSize + endY

        elements.add(LineTo(x1, y1))
        elements.add(LineTo(x2, y2))
        elements.add(LineTo(endX, endY))
    }

    companion object {
        private const val DEFAULT_ARROW_HEAD_SIZE = 5.0
    }
}