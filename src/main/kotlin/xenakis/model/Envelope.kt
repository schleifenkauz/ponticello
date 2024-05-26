package xenakis.model

import hextant.core.editor.ViewManager
import javafx.geometry.HorizontalDirection
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

    fun code(offset: Double, dur: Double, doneAction: String = "Done.freeSelf"): String {
        require(offset <= dur)
        val afterOffset = cut(offset / dur, whichHalf = HorizontalDirection.RIGHT)
        val points = afterOffset.points.map { (x, y) -> Point(x * dur, y) }
        val levels = points.map { (_, y) -> y.format(2) }
        val times = points.zipWithNext { a, b -> (b.x - a.x).format(2) }
        return "EnvGen.kr(Env.new(levels: $levels, times: $times, curve: $curve), doneAction: $doneAction)"
    }

    private fun interpolateValueAt(t: Double): Double {
        var i = points.binarySearch(Point(t, 0.0))
        if (i >= 0) return points[i].y
        i = -(i + 1)
        if (i == 0 || i == points.size) error("time $t outside of envelope range $points")
        val (x1, y1) = points[i - 1]
        val (x2, y2) = points[i]
        val slope = (y2 - y1) / (x2 - x1)
        val dx = t - x1
        return y1 + slope * dx
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

    fun cut(position: Double, whichHalf: HorizontalDirection): Envelope {
        var i = points.binarySearch(Point(position, 0.0))
        if (i < 0) i = -(i + 1)
        val valueAtPos = interpolateValueAt(position)
        val pivot = Point(position, valueAtPos)
        return when (whichHalf) {
            HorizontalDirection.LEFT -> {
                val left = points.take(i) + pivot
                Envelope(left.mapTo(mutableListOf()) { (x, y) -> Point(x / position, y) }, curve)
            }

            HorizontalDirection.RIGHT -> {
                val right = listOf(pivot) + points.drop(i)
                Envelope(right.mapTo(mutableListOf()) { (x, y) -> Point((x - position) / (1 - position), y) }, curve)
            }
        }
    }

    companion object {
        fun constant(value: Double, curve: Warp) = Envelope(mutableListOf(Point(0.0, value), Point(1.0, value)), curve)

        val default = constant(1.0, Warp.Linear)
    }
}