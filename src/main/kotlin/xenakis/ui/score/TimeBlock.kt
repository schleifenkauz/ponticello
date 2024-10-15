package xenakis.ui.score

import xenakis.impl.Decimal
import xenakis.model.score.ObjectPosition

interface TimeBlock {
    val absolutePosition: ObjectPosition

    fun getDuration(width: Double): Decimal

    fun getTime(x: Double): Decimal

    fun getWidth(duration: Decimal): Double

    fun getX(time: Decimal): Double
}