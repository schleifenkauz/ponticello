package xenakis.model

import xenakis.ui.format
import kotlin.math.abs

data class ObjectPosition(val time: Double, val y: Double) : Comparable<ObjectPosition> {
    override fun compareTo(other: ObjectPosition): Int {
        val dt = time - other.time
        if (dt >= DELTA) return +1
        if (dt <= -DELTA) return -1
        val dy = y - other.y
        if (dy >= DELTA) return +1
        if (dy <= -DELTA) return -1
        return 0
    }

    operator fun plus(position: ObjectPosition): ObjectPosition =
        ObjectPosition(time + position.time, y + position.y)

    override fun toString(): String = "(${time.format(2)}, ${y.format(1)})"

    override fun equals(other: Any?): Boolean = when {
        other !is ObjectPosition -> false
        abs(time - other.time) > DELTA -> false
        abs(y - other.y) > DELTA -> false
        else -> true
    }

    override fun hashCode(): Int = 37 * (time * 1000).toLong().hashCode() + y.hashCode()

    companion object {
        private const val DELTA = 0.001

        val ZERO = ObjectPosition(0.0, 0.0)
    }
}