package ponticello.ui.score

import ponticello.impl.Decimal
import ponticello.impl.DecimalRange
import ponticello.model.score.ObjectPosition

interface TimeBlock {
    val absolutePosition: ObjectPosition

    val timeRange: DecimalRange

    fun getDuration(width: Double): Decimal

    fun getTime(x: Double): Decimal

    fun getWidth(duration: Decimal): Double

    fun getX(time: Decimal): Double
}