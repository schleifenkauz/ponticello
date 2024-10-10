package xenakis.ui

import xenakis.impl.Decimal
import xenakis.model.ObjectPosition

interface TimeBlock {
    val absolutePosition: ObjectPosition

    fun getDuration(width: Double): Decimal

    fun getTime(x: Double): Decimal

    fun getWidth(duration: Decimal): Double

    fun getX(time: Decimal): Double
}