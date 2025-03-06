package xenakis.ui.controls

import fxutils.prompt.TextPrompt
import xenakis.impl.*

class DecimalPrompt(
    title: String, private val precision: Int, initialValue: Decimal?,
    private val range: DecimalRange = (-Decimal.INF)..Decimal.INF
) : TextPrompt<Decimal>(title, initialValue?.toString().orEmpty()) {
    constructor(title: String, initialValue: Decimal, range: DecimalRange = (-Decimal.INF)..Decimal.INF) : this(
        title, initialValue.precision, initialValue, range
    )

    constructor(title: String, precision: Int, initialValue: Double, range: DoubleRange) : this(
        title, precision, initialValue.toDecimal(), range.asDecimal
    )

    override fun convert(text: String): Decimal? = text.parseDecimal()?.takeIf { v -> v in range }?.round(precision)
}
