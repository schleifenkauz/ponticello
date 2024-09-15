package xenakis.model

import kotlin.math.abs

data class ObjectPosition(val time: Double, val y: Double) : Comparable<ObjectPosition> {
    override fun compareTo(other: ObjectPosition): Int =
        compareValuesBy(this, other, ObjectPosition::time, ObjectPosition::y)

    operator fun plus(position: ObjectPosition): ObjectPosition =
        ObjectPosition(time + position.time, y + position.y)

    override fun toString(): String = "time: $time, y: $y"

    override fun equals(other: Any?): Boolean = when {
        other !is ObjectPosition -> false
        abs(time - other.time) > DELTA -> false
        abs(y - other.y) > DELTA -> false
        else -> true
    }

    override fun hashCode(): Int = 37 * (time * 1000).toLong().hashCode() + y.hashCode()

    companion object {
        private const val DELTA = 0.001
    }
}