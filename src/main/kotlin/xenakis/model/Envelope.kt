package xenakis.model

import hextant.core.editor.ViewManager
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import xenakis.impl.Point
import xenakis.sc.Warp
import xenakis.ui.EnvelopeView
import xenakis.ui.format

@Serializable
data class Envelope(private val _points: MutableList<Point>, val curve: Warp) {
    @Transient
    private val viewManager = ViewManager.createWeakViewManager<EnvelopeView>()

    val points: List<Point> get() = _points

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
        val levels = ps.map { it.y.format(2) }
        val times = ps.zipWithNext { a, b -> (b.x - a.x).format(2) }
        return "EnvGen.kr(Env.new(levels: $levels, times: $times, curve: $curve), doneAction: Done.freeSelf)"
    }

    fun clone() = copy()

    fun addPoint(idx: Int, point: Point) {
        _points.add(idx, point)
        viewManager.notifyViews { addedPoint(idx, point) }
    }

    fun editPoint(idx: Int, newPoint: Point) {
        _points[idx] = newPoint
        viewManager.notifyViews { changedPoint(idx, newPoint) }
    }

    fun removePoint(idx: Int) {
        val p = _points.removeAt(idx)
        viewManager.notifyViews { removedPoint(idx, p) }
    }

    fun addView(view: EnvelopeView) {
        viewManager.addView(view)
    }

    fun removeView(view: EnvelopeView) {
        viewManager.removeView(view)
    }

    companion object {
        fun constant(value: Double, curve: Warp) = Envelope(mutableListOf(Point(0.0, value), Point(1.0, value)), curve)

        val default = constant(1.0, Warp.Linear)
    }
}