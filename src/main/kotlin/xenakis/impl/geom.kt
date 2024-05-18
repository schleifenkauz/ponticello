package xenakis.impl

import kotlinx.serialization.Serializable
import kotlin.math.pow
import kotlin.math.sqrt

@Serializable
data class Point(val x: Double, val y: Double)

infix fun Point.dist(p: Point) = sqrt((p.x - x).pow(2) + (p.y - y).pow(2))