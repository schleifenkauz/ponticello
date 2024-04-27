package xenakis.sc

import kotlinx.serialization.Serializable
import xenakis.impl.Point
import xenakis.ui.format

@Serializable
data class Envelope(val points: MutableList<Point>, val curve: Warp) {
    fun code(offset: Double, dur: Double): String {
        require(offset <= dur)
        var ps = points.map { (x, y) -> Point(x * dur, y) }
        if (offset > 0.0) {
            val withoutIrrelevant = ps.dropWhile { p -> p.x < offset }
            val firstTaken = withoutIrrelevant[0]
            if (firstTaken.x > offset) {
                val lastDropped = ps[ps.size - 1 - withoutIrrelevant.size]
                val constant = lastDropped.y
                val slope = (firstTaken.y - lastDropped.y) / (firstTaken.x - lastDropped.x)
                val x = offset - lastDropped.x
                val interpolatedY = constant + slope * x
                ps = listOf(Point(offset, interpolatedY)) + withoutIrrelevant
            }
        }
        val levels = ps.map { it.y.format(1) }
        val times = ps.zipWithNext { a, b -> (b.x - a.x).format(1) }
        return "EnvGen.kr(Env.new(levels: $levels, times: $times, curve: $curve), doneAction: Done.freeSelf)"
    }

    fun clone() = copy()

    companion object {
        fun constant(value: Double, curve: Warp) = Envelope(mutableListOf(Point(0.0, value), Point(1.0, value)), curve)
    }
}