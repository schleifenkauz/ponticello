package xenakis.model

import hextant.core.editor.ListenerManager
import javafx.geometry.HorizontalDirection
import javafx.geometry.HorizontalDirection.LEFT
import javafx.geometry.HorizontalDirection.RIGHT
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import xenakis.impl.Point
import xenakis.sc.NumericalControlSpec
import xenakis.sc.Warp
import xenakis.sc.map
import xenakis.sc.unmap
import xenakis.ui.EnvelopeView
import kotlin.math.absoluteValue

@Serializable
class Envelope(private val _points: MutableList<Point>, val curve: Warp) {
    @Transient
    private val viewManager = ListenerManager.createWeakListenerManager<EnvelopeView>()

    val points: List<Point> get() = _points

    val duration get() = points.last().x

    fun code(offset: Double, doneAction: String = "Done.freeSelf"): String {
        require(offset <= duration) { "Invalid offset: $offset, duration is $duration" }
        val afterOffset = if (offset == 0.0) this else cut(offset, whichHalf = RIGHT)
        val levels = afterOffset.points.map { (_, y) -> y.toString() }
        val times = afterOffset.points.zipWithNext { a, b -> (b.x - a.x).toString() }
        return "EnvGen.kr(Env.new(levels: $levels, times: $times, curve: $curve), doneAction: $doneAction)"
    }

    fun interpolateValueAt(t: Double): Double {
        var i = points.binarySearch(Point(t, 0.0))
        if (i >= 0) return points[i].y
        i = -(i + 1)
        val (x1, y1) = if (i == 0) points[1] else points[i - 1]
        val (x2, y2) = if (i == points.size) points[i - 2] else points[i]
        val slope = (curve.map(y2) - curve.map(y1)) / (x2 - x1)
        val dx = t - x1
        val dy = slope * dx
        return curve.unmap(curve.map(y1) + dy)
    }

    fun copy() = Envelope(_points.toMutableList(), curve)

    fun addPoint(idx: Int, point: Point) {
        _points.add(idx, point)
        viewManager.notifyListeners { addedPoint(idx, point) }
    }

    fun editPoint(idx: Int, newPoint: Point) {
        _points[idx] = newPoint
        viewManager.notifyListeners { changedPoint(idx, newPoint) }
    }

    fun editPoint(idx: Int, newY: Double) {
        val newPoint = points[idx].copy(y = newY)
        editPoint(idx, newPoint)
    }

    fun removePoint(idx: Int) {
        val p = _points.removeAt(idx)
        viewManager.notifyListeners { removedPoint(idx, p) }
    }

    fun addView(view: EnvelopeView) {
        viewManager.addListener(view)
    }

    fun removeView(view: EnvelopeView) {
        viewManager.removeListener(view)
    }

    fun cut(position: Double, whichHalf: HorizontalDirection): Envelope {
        var i = points.binarySearch(Point(position, 0.0))
        if (i < 0) i = -(i + 1)
        val valueAtPos = interpolateValueAt(position)
        val pivot = Point(position, valueAtPos)
        return when (whichHalf) {
            LEFT -> {
                val left = points.take(i) + pivot
                Envelope(left.toMutableList(), curve)
            }

            RIGHT -> {
                val right = listOf(pivot) + points.drop(i)
                Envelope(right.mapTo(mutableListOf()) { (x, y) -> Point((x - position), y) }, curve)
            }
        }
    }

    private fun shiftAll(points: Iterable<IndexedValue<Point>>, delta: Double) {
        for ((i, p) in points) editPoint(i, p.copy(x = p.x + delta))
    }

    fun resize(newDur: Double, dir: HorizontalDirection, spec: NumericalControlSpec) {
        val deltaDur = newDur - duration
        when {
            newDur == duration -> return
            dir == LEFT && newDur > duration -> {
                val y = interpolateValueAt(duration - newDur).coerceIn(spec.range)
                shiftAll(points.withIndex().drop(1), deltaDur)
                editPoint(0, Point(x = 0.0, y = y))
            }

            dir == LEFT && newDur < duration -> {
                val y = interpolateValueAt(0.0).coerceIn(spec.range)
                for ((i, p) in points.toList().withIndex()) {
                    if (p.x < (duration - newDur)) removePoint(i)
                }
                shiftAll(points.withIndex(), deltaDur)
                if (points[0].x.absoluteValue > 0.01) {
                    addPoint(0, Point(x = 0.0, y = y))
                } else {
                    editPoint(0, points[0].copy(x = 0.0))
                }
            }

            dir == RIGHT && newDur > duration -> {
                val y = interpolateValueAt(newDur).coerceIn(spec.range)
                editPoint(points.size - 1, Point(newDur, y))
            }

            dir == RIGHT && newDur < duration -> {
                val y = interpolateValueAt(newDur).coerceIn(spec.range).coerceIn(spec.range)
                for ((i, p) in points.withIndex().reversed()) {
                    if (p.x > newDur) removePoint(i)
                }
                if ((duration - newDur).absoluteValue > 0.01) {
                    addPoint(points.size, Point(x = newDur, y = y))
                } else {
                    editPoint(points.size - 1, points.last().copy(x = newDur))
                }
            }
        }
    }

    fun rescale(newDur: Double) {
        val oldDur = points.last().x
        val factor = newDur / oldDur
        for ((i, p) in points.withIndex()) {
            editPoint(i, p.copy(x = p.x * factor))
        }
    }

    fun reverse() {
        val points = points.toList()
        for (idx in points.indices) {
            editPoint(idx, points[points.size - 1 - idx])
        }
    }

    companion object {
        fun constant(value: Double, duration: Double, curve: Warp) =
            Envelope(mutableListOf(Point(0.0, value), Point(duration, value)), curve)

        val default = constant(1.0, 1.0, Warp.Linear)
    }
}