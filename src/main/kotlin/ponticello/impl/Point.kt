package ponticello.impl

import javafx.geometry.Point2D
import kotlinx.serialization.Serializable

@Serializable
data class Point(val x: Double, val y: Double) {
    val point2d get() = Point2D(x, y)

    operator fun plus(other: Point) = Point(x + other.x, y + other.y)

    companion object {
        fun from(point2D: Point2D) = Point(point2D.x, point2D.y)
    }
}