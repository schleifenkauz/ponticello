package xenakis.model

import hextant.context.Context
import hextant.core.editor.ListenerManager
import hextant.undo.AbstractEdit
import hextant.undo.Edit
import hextant.undo.UndoManager
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
class Envelope(private val _points: MutableList<Point>, private val curve: Warp) {
    @Transient
    private val viewManager = ListenerManager.createWeakListenerManager<EnvelopeView>()

    @Transient
    private lateinit var context: Context

    val points: List<Point> get() = _points

    val duration get() = points.last().x

    @Transient
    private var editedIndex: Int = -1

    @Transient
    private var pointBeforeEdit: Point? = null

    @Transient
    private var segmentValueBeforeEdit: Double = Double.NaN

    fun initialize(context: Context) {
        this.context = context
    }

    fun code(doneAction: String = "Done.freeSelf"): String {
        val levels = points.map { (_, y) -> y.toString() }
        val times = points.zipWithNext { a, b -> (b.x - a.x).toString() }
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

    fun addPoint(idx: Int, point: Point, undoable: Boolean = false) {
        _points.add(idx, point)
        viewManager.notifyListeners { addedPoint(idx, point) }
        if (undoable) context[UndoManager].record(AddPoint(point, idx, this))
    }

    fun beginPointEdit(idx: Int) {
        editedIndex = idx
        pointBeforeEdit = points[idx]
    }

    fun beginSegmentEdit(idx: Int) {
        editedIndex = idx
        segmentValueBeforeEdit = points[idx].y
    }

    fun editPoint(newPoint: Point) {
        check(editedIndex != -1) { "no edit begun" }
        modifyPoint(editedIndex, newPoint)
    }

    fun editSegment(value: Double) {
        check(editedIndex != -1) { "no edit begun" }
        editSegment(editedIndex, value)
    }

    private fun editSegment(idx: Int, value: Double) {
        modifyPoint(idx, points[idx].copy(y = value))
        modifyPoint(idx + 1, points[idx + 1].copy(y = value))
    }

    private fun modifyPoint(idx: Int, newPoint: Point) {
        _points[idx] = newPoint
        viewManager.notifyListeners { changedPoint(idx, newPoint) }
    }

    fun editPoint(idx: Int, newY: Double) {
        beginPointEdit(idx)
        val newPoint = points[idx].copy(y = newY)
        editPoint(newPoint)
        finishEdit()
    }

    fun adjustPointVertical(idx: Int, delta: Double) {
        editPoint(idx, points[idx].y + delta)
    }

    fun adjustPointHorizontal(idx: Int, delta: Double) {
        val p = points[idx]
        if (idx == 0 || idx == points.size - 1) return
        val newX = (p.x + delta).coerceIn(points[idx - 1].x, points[idx + 1].x)
        beginPointEdit(idx)
        editPoint(p.copy(x = newX))
        finishEdit()
    }

    fun finishEdit() {
        if (editedIndex == -1) return
        val p = points[editedIndex]
        val edit = when {
            pointBeforeEdit != null -> EditPoint(editedIndex, pointBeforeEdit!!, p, this)
            !segmentValueBeforeEdit.isNaN() -> EditSegment(editedIndex, segmentValueBeforeEdit, p.y, this)
            else -> throw IllegalStateException("Neither point nor segment edit")
        }
        context[UndoManager].record(edit)
        editedIndex = -1
        pointBeforeEdit = null
        segmentValueBeforeEdit = Double.NaN
    }

    fun removePoint(idx: Int, undoable: Boolean = true) {
        val p = _points.removeAt(idx)
        viewManager.notifyListeners { removedPoint(idx, p) }
        if (undoable) context[UndoManager].record(RemovePoint(p, idx, this))
    }

    private fun editPoint(idx: Int, p: Point) {
        beginPointEdit(idx)
        editPoint(p)
        finishEdit()
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
        for ((i, p) in points) modifyPoint(i, p.copy(x = p.x + delta))
    }

    fun resize(newDur: Double, dir: HorizontalDirection, spec: NumericalControlSpec) {
        val deltaDur = newDur - duration
        when {
            newDur == duration -> return
            dir == LEFT && newDur > duration -> {
                val y = interpolateValueAt(duration - newDur).coerceIn(spec.range)
                shiftAll(points.withIndex().drop(1), deltaDur)
                modifyPoint(0, Point(x = 0.0, y = y))
            }

            dir == LEFT && newDur < duration -> {
                val y = interpolateValueAt(0.0).coerceIn(spec.range)
                for ((i, p) in points.toList().withIndex()) {
                    if (p.x < (duration - newDur)) removePoint(i, undoable = false)
                }
                shiftAll(points.withIndex(), deltaDur)
                if (points[0].x.absoluteValue > 0.01) {
                    addPoint(0, Point(x = 0.0, y = y), undoable = false)
                } else {
                    modifyPoint(0, points[0].copy(x = 0.0))
                }
            }

            dir == RIGHT && newDur > duration -> {
                val y = interpolateValueAt(newDur).coerceIn(spec.range)
                modifyPoint(points.size - 1, Point(newDur, y))
            }

            dir == RIGHT && newDur < duration -> {
                val y = interpolateValueAt(newDur).coerceIn(spec.range).coerceIn(spec.range)
                for ((i, p) in points.withIndex().reversed()) {
                    if (p.x > newDur) removePoint(i, undoable = false)
                }
                if ((duration - newDur).absoluteValue > 0.01) {
                    addPoint(points.size, Point(x = newDur, y = y), undoable = false)
                } else {
                    modifyPoint(points.size - 1, points.last().copy(x = newDur))
                }
            }
        }
    }

    fun rescale(newDur: Double) {
        val oldDur = points.last().x
        val factor = newDur / oldDur
        for ((i, p) in points.withIndex()) {
            modifyPoint(i, p.copy(x = p.x * factor))
        }
    }

    fun reverse() {
        val points = points.toList()
        for (idx in points.indices) {
            modifyPoint(idx, points[points.size - 1 - idx])
        }
    }

    private class AddPoint(val point: Point, val idx: Int, val envelope: Envelope) : AbstractEdit() {
        override val actionDescription: String
            get() = "Add envelope point"

        override fun doUndo() {
            envelope.removePoint(idx)
        }

        override fun doRedo() {
            envelope.addPoint(idx, point)
        }
    }

    private class RemovePoint(val point: Point, val idx: Int, val envelope: Envelope) : AbstractEdit() {
        override val actionDescription: String
            get() = "Remove envelope point"

        override fun doUndo() {
            envelope.addPoint(idx, point)
        }

        override fun doRedo() {
            envelope.removePoint(idx)
        }
    }

    private class EditSegment(
        private val idx: Int,
        private val oldValue: Double,
        private val newValue: Double,
        private val envelope: Envelope
    ) : AbstractEdit() {
        override val actionDescription: String
            get() = "Edit envelope segment"

        override fun doRedo() {
            envelope.editSegment(idx, newValue)
        }

        override fun doUndo() {
            envelope.editSegment(idx, oldValue)
        }
    }

    private class EditPoint(
        private val idx: Int,
        private val oldPoint: Point,
        private val newPoint: Point,
        private val envelope: Envelope
    ) : AbstractEdit() {
        override val actionDescription: String
            get() = "Edit envelope point"

        override fun doUndo() {
            envelope.editPoint(idx, oldPoint)
        }

        override fun doRedo() {
            envelope.editPoint(idx, newPoint)
        }

        override fun mergeWith(other: Edit): Edit? {
            if (other !is EditPoint) return null
            if (other.envelope != this.envelope) return null
            if (other.idx != this.idx) return null
            return EditPoint(idx, this.oldPoint, other.newPoint, envelope)
        }
    }

    companion object {
        fun constant(value: Double, duration: Double, curve: Warp) =
            Envelope(mutableListOf(Point(0.0, value), Point(duration, value)), curve)

        val default = constant(1.0, 1.0, Warp.Linear)
    }
}