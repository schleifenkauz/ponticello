package xenakis.model

data class ObjectPosition(val time: Double, val y: Double) : Comparable<ObjectPosition> {
    override fun compareTo(other: ObjectPosition): Int =
        compareValuesBy(this, other, ObjectPosition::time, ObjectPosition::y)

    operator fun plus(position: ObjectPosition): ObjectPosition =
        ObjectPosition(time + position.time, y + position.y)

    override fun toString(): String = "time: $time, y: $y"
}