package xenakis.impl

import kotlinx.serialization.Serializable
import kotlin.math.pow
import kotlin.math.sqrt

@Serializable
data class Point(var x: Double, var y: Double)

infix fun Point.dist(p: Point) = sqrt((p.x - x).pow(2) + (p.y - y).pow(2))

@Serializable
data class Rect(var x: Double, var y: Double, var width: Double, var height: Double) {
    fun set(x: Double, y: Double, width: Double, height: Double) {
        this.x = x
        this.y = y
        this.width = width
        this.height = height
    }
}