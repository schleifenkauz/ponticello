package xenakis.impl

import kotlinx.serialization.Serializable
import kotlin.math.pow
import kotlin.math.sqrt

@Serializable
data class Point(val x: Double, val y: Double) : Comparable<Point> {
    infix fun dist(p: Point) = sqrt((p.x - x).pow(2) + (p.y - y).pow(2))

    override fun compareTo(other: Point): Int = compareValuesBy(this, other, Point::x, Point::y)
}
