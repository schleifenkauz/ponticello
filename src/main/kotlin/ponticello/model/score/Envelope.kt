package ponticello.model.score

import fxutils.undo.AbstractEdit
import fxutils.undo.Edit
import fxutils.undo.UndoManager
import hextant.context.Context
import hextant.core.editor.ListenerManager
import javafx.geometry.HorizontalDirection
import javafx.geometry.HorizontalDirection.LEFT
import javafx.geometry.HorizontalDirection.RIGHT
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.listSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import ponticello.impl.*
import ponticello.sc.*
import ponticello.ui.score.EnvelopeView
import kotlin.math.max

@Serializable(Envelope.Serializer::class)
class Envelope(private val _points: MutableList<EnvelopePoint>) {
    @Transient
    private val viewManager = ListenerManager.createWeakListenerManager<EnvelopeView>()

    @Transient
    lateinit var context: Context

    val points: List<EnvelopePoint> get() = _points

    val duration get() = points.last().time

    @Transient
    private var editedIndex: Int = -1

    @Transient
    private var pointBeforeEdit: EnvelopePoint? = null

    @Transient
    private var segmentValueBeforeEdit: Decimal = Decimal.NaN

    fun initialize(context: Context) {
        this.context = context
        //this is only for needed when opening projects that were created before the decimal-precision update
        val itr = _points.listIterator()
        for (p in itr) {
            itr.set(
                EnvelopePoint(
                    p.time.withPrecision(ObjectPosition.TIME_PRECISION),
                    p.value.withPrecision(ObjectPosition.Y_PRECISION)
                )
            )
        }
    }

    fun code(defaultWarp: Warp): String {
        val levels = points.map { (_, y) -> y.toString() }
        val times = points.zipWithNext { a, b -> (b.time - a.time).toString() }
        val curves = points.drop(1).map { p -> (p.curve ?: defaultWarp) }
        val curve =
            if (curves.all { it == defaultWarp }) defaultWarp.code(context)
            else curves.joinToString(",", "[", "]")
        return "Env.new(levels: $levels, times: $times, curve: $curve)"
    }

    fun generatorCode(warp: Warp, offset: Decimal): String {
        val envCode = code(warp)
        return "IEnvGen.kr($envCode, index: Sweep.kr(rate: ~time_warp_bus.kr) + $offset)"
    }


    fun interpolateValueAt(t: Decimal, warp: Warp): Decimal {
        var i = points.binarySearch(EnvelopePoint(t, zero))
        if (i >= 0) return points[i].value
        i = -(i + 1)
        val (x1, y1) = if (i == 0) points[1] else points[i - 1]
        val (x2, y2) = if (i == points.size) points[i - 2] else points[i]
        val precision = max(y1.precision, y2.precision)
        val slope = (warp.map(y2.value) - warp.map(y1.value)) / (x2 - x1)
        val dx = t - x1
        val dy = slope * dx
        return warp.unmap((warp.map(y1.toDouble()) + dy).toDouble()).withPrecision(precision)
    }

    fun copy() = Envelope(_points.toMutableList())

    fun addPoint(idx: Int, point: EnvelopePoint, undoable: Boolean = false) {
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
        segmentValueBeforeEdit = points[idx].value
    }

    fun editPoint(newPoint: EnvelopePoint) {
        check(editedIndex != -1) { "no edit begun" }
        modifyPoint(editedIndex, newPoint)
    }

    fun editSegment(value: Decimal) {
        check(editedIndex != -1) { "no edit begun" }
        editSegment(editedIndex, value)
    }

    private fun editSegment(idx: Int, value: Decimal) {
        modifyPoint(idx, points[idx].copy(value = value))
        modifyPoint(idx + 1, points[idx + 1].copy(value = value))
    }

    private fun modifyPoint(idx: Int, newPoint: EnvelopePoint) {
        _points[idx] = newPoint
        viewManager.notifyListeners { changedPoint(idx, newPoint) }
    }

    fun editPoint(idx: Int, newY: Decimal) {
        beginPointEdit(idx)
        val newPoint = points[idx].copy(value = newY)
        editPoint(newPoint)
        finishEdit()
    }

    fun adjustPointVertical(idx: Int, delta: Decimal) {
        editPoint(idx, points[idx].value + delta)
    }

    fun adjustPointHorizontal(idx: Int, delta: Decimal) {
        val p = points[idx]
        if (idx == 0 || idx == points.size - 1) return
        val newT = (p.time + delta).coerceIn(points[idx - 1].time, points[idx + 1].time)
        beginPointEdit(idx)
        editPoint(p.copy(time = newT))
        finishEdit()
    }

    fun finishEdit() {
        if (editedIndex == -1) return
        val p = points[editedIndex]
        val edit = when {
            pointBeforeEdit != null -> EditPoint(editedIndex, pointBeforeEdit!!, p, this)
            !segmentValueBeforeEdit.isNaN() -> EditSegment(editedIndex, segmentValueBeforeEdit, p.value, this)
            else -> throw IllegalStateException("Neither point nor segment edit")
        }
        context[UndoManager].record(edit)
        editedIndex = -1
        pointBeforeEdit = null
        segmentValueBeforeEdit = Decimal.NaN
    }

    fun removePoint(idx: Int, undoable: Boolean = true) {
        val p = _points.removeAt(idx)
        viewManager.notifyListeners { removedPoint(idx, p) }
        if (undoable) context[UndoManager].record(RemovePoint(p, idx, this))
    }

    private fun editPoint(idx: Int, p: EnvelopePoint) {
        beginPointEdit(idx)
        editPoint(p)
        finishEdit()
    }

    fun addListener(view: EnvelopeView) {
        viewManager.addListener(view)
    }

    fun removeView(view: EnvelopeView) {
        viewManager.removeListener(view)
    }

    fun cut(position: Decimal, whichHalf: HorizontalDirection, warp: Warp): Envelope {
        var i = points.binarySearch(EnvelopePoint(position, zero(0)))
        if (i < 0) i = -(i + 1)
        val valueAtPos = interpolateValueAt(position, warp)
        val pivot = EnvelopePoint(position, valueAtPos)
        return when (whichHalf) {
            LEFT -> {
                val left = points.take(i) + pivot
                Envelope(left.toMutableList())
            }

            RIGHT -> {
                val right = listOf(pivot) + points.drop(i)
                Envelope(right.mapTo(mutableListOf()) { (x, y) -> EnvelopePoint((x - position), y) })
            }
        }
    }

    private fun shiftAll(points: Iterable<IndexedValue<EnvelopePoint>>, delta: Decimal) {
        for ((i, p) in points) modifyPoint(i, p.copy(time = p.time + delta))
    }

    fun resize(newDur: Decimal, dir: HorizontalDirection, spec: NumericalControlSpec) {
        val deltaDur = newDur - duration
        when {
            newDur == duration -> return
            dir == LEFT && newDur > duration -> {
                val y = interpolateValueAt(duration - newDur, spec.warp).coerceIn(spec.range)
                shiftAll(points.withIndex().drop(1), deltaDur)
                modifyPoint(0, EnvelopePoint(time = zero, value = y))
            }

            dir == LEFT && newDur < duration -> {
                val y = interpolateValueAt(zero, spec.warp).coerceIn(spec.range)
                for ((i, p) in points.toList().withIndex()) {
                    if (p.time < (duration - newDur)) removePoint(i, undoable = false)
                }
                shiftAll(points.withIndex(), deltaDur)
                if (points[0].time.absoluteValue > 0.01) {
                    addPoint(0, EnvelopePoint(time = zero, value = y), undoable = false)
                } else {
                    modifyPoint(0, points[0].copy(time = zero))
                }
            }

            dir == RIGHT && newDur > duration -> {
                val y = interpolateValueAt(newDur, spec.warp).coerceIn(spec.range)
                modifyPoint(points.size - 1, EnvelopePoint(newDur, y))
            }

            dir == RIGHT && newDur < duration -> {
                val y = interpolateValueAt(newDur, spec.warp).coerceIn(spec.range)
                for ((i, p) in points.withIndex().reversed()) {
                    if (p.time > newDur) removePoint(i, undoable = false)
                }
                if ((duration - newDur).absoluteValue > 0.01) {
                    addPoint(points.size, EnvelopePoint(time = newDur, value = y), undoable = false)
                } else {
                    modifyPoint(points.size - 1, points.last().copy(time = newDur))
                }
            }
        }
    }

    fun rescale(newDur: Decimal) {
        val oldDur = points.last().time
        val factor = newDur / oldDur
        for ((i, p) in points.withIndex()) {
            modifyPoint(i, p.copy(time = p.time * factor))
        }
    }

    fun reverse() {
        val points = points.toList()
        for (idx in points.indices) {
            modifyPoint(idx, points[points.size - 1 - idx])
        }
    }

    private class AddPoint(val point: EnvelopePoint, val idx: Int, val envelope: Envelope) : AbstractEdit() {
        override val actionDescription: String
            get() = "Add envelope point"

        override fun doUndo() {
            envelope.removePoint(idx)
        }

        override fun doRedo() {
            envelope.addPoint(idx, point)
        }
    }

    private class RemovePoint(val point: EnvelopePoint, val idx: Int, val envelope: Envelope) : AbstractEdit() {
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
        private val oldValue: Decimal,
        private val newValue: Decimal,
        private val envelope: Envelope,
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
        private val oldPoint: EnvelopePoint,
        private val newPoint: EnvelopePoint,
        private val envelope: Envelope,
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

    @Serializable
    data class EnvelopePoint(
        val time: Decimal,
        val value: Decimal,
        val curve: Warp? = null,
    ) : Comparable<EnvelopePoint> {
        override fun compareTo(other: EnvelopePoint): Int =
            compareValuesBy(this, other, EnvelopePoint::time, EnvelopePoint::value)
    }

    object Serializer : KSerializer<Envelope> {
        override val descriptor: SerialDescriptor
            get() = listSerialDescriptor<EnvelopePoint>()

        override fun serialize(encoder: Encoder, value: Envelope) {
            encoder.encodeSerializableValue(ListSerializer(EnvelopePoint.serializer()), value.points)
        }

        override fun deserialize(decoder: Decoder): Envelope {
            val points = decoder.decodeSerializableValue(ListSerializer(EnvelopePoint.serializer()))
            return Envelope(points as MutableList)
        }
    }

    companion object {
        fun constant(value: Decimal, duration: Decimal) = Envelope(
            mutableListOf(EnvelopePoint(zero(duration.precision), value), EnvelopePoint(duration, value)),
        )

        fun getDefault(spec: NumericalControlSpec) = constant(spec.min.get(), spec.max.get())
    }
}