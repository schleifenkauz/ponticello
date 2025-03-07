package xenakis.model.score

import fxutils.format
import xenakis.impl.Decimal
import xenakis.impl.unaryMinus
import xenakis.impl.withPrecision

data class ObjectPosition(val time: Decimal, val y: Decimal) : Comparable<ObjectPosition> {
    constructor(time: Double, y: Double) : this(time.withPrecision(TIME_PRECISION), y.withPrecision(Y_PRECISION))

    override fun compareTo(other: ObjectPosition): Int =
        compareValuesBy(this, other, ObjectPosition::time, ObjectPosition::y)

    operator fun plus(position: ObjectPosition): ObjectPosition =
        ObjectPosition(time + position.time, y + position.y)

    operator fun minus(position: ObjectPosition) = ObjectPosition(time - position.time, y - position.y)

    infix fun plusTime(time: Decimal) = ObjectPosition(this.time + time, y)

    infix fun plusY(y: Decimal) = ObjectPosition(time, this.y + y)

    override fun toString(): String = "($time, $y)"

    operator fun unaryMinus(): ObjectPosition = ObjectPosition(-time, -y)

    companion object {
        const val TIME_PRECISION = 4
        const val Y_PRECISION = 3

        val ZERO = ObjectPosition(0.0, 0.0)

        @JvmStatic
        fun main(args: Array<String>) {
            println(0.1 + 0.2)
            println((0.1 + 0.2).format(4))
        }
    }
}