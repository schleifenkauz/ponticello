package ponticello.ui.impl

import fxutils.controls.AbstractSpinner
import ponticello.impl.Decimal
import ponticello.impl.parseDecimal
import ponticello.impl.withMaxPrecision
import ponticello.impl.withPrecision
import reaktive.value.ReactiveVariable

class DecimalSpinner(
    value: ReactiveVariable<Decimal>,
    min: Decimal, max: Decimal,
    private val step: Decimal,
    private val maxPrecision: Int = 2,
) : AbstractSpinner<Decimal>(value, min, max) {
    init {
        bind()
    }

    override fun increment(value: Decimal): Decimal = (value + step).withMaxPrecision(maxPrecision)

    override fun decrement(value: Decimal): Decimal = (value - step).withPrecision(maxPrecision)

    override fun parseValue(text: String): Decimal? = text.parseDecimal()?.withMaxPrecision(maxPrecision)

    override fun toString(value: Decimal): String = value.withPrecision(maxPrecision).toString()
}