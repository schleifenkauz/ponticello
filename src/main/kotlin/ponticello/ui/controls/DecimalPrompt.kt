package ponticello.ui.controls

import fxutils.prompt.TextPrompt
import ponticello.impl.*
import ponticello.sc.NumericalControlSpec

open class DecimalPrompt(
    title: String, private val precision: Int, initialValue: Decimal?,
    private val range: DecimalRange = (-Decimal.INF)..Decimal.INF
) : TextPrompt<Decimal>(title, initialValue?.toString().orEmpty()) {
    constructor(
        title: String, initialValue: Decimal,
        precision: Int = initialValue.precision, range: DecimalRange = (-Decimal.INF)..Decimal.INF
    ) : this(title, precision, initialValue, range)

    constructor(title: String, precision: Int, initialValue: Double, range: DoubleRange) : this(
        title, precision, initialValue.toDecimal(), range.asDecimal
    )

    constructor(title: String, spec: NumericalControlSpec, initialValue: Decimal? = null) : this(
        title, spec.precision, initialValue, spec.range
    )

    override fun convert(text: String): Decimal? = text.parseDecimal()?.takeIf { v -> v in range }?.round(precision)
}
