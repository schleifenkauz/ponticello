package ponticello.ui.controls

import fxutils.prompt.TextPrompt
import javafx.scene.input.KeyEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ponticello.impl.*
import ponticello.sc.NumericalControlSpec
import ponticello.sc.client.SuperColliderClient
import ponticello.sc.client.eval
import java.util.concurrent.TimeUnit

open class DecimalPrompt(
    title: String, initialValue: Decimal?, private val precision: Int = -1,
    private val range: DecimalRange = (-Decimal.INF)..Decimal.INF,
    private val client: SuperColliderClient? = null
) : TextPrompt<Decimal>(title, initialValue?.toString().orEmpty()) {

    constructor(title: String, precision: Int, initialValue: Double, range: DoubleRange) : this(
        title, initialValue.toDecimal(), precision, range.asDecimal
    )

    constructor(title: String, spec: NumericalControlSpec, initialValue: Decimal? = null) : this(
        title, initialValue, spec.precision, spec.range
    )

    override suspend fun convert(text: String, ev: KeyEvent): Decimal? {
        val decimalValue = text.parseDecimal()
        val value = when {
            decimalValue != null && decimalValue in range -> decimalValue
            client != null && ev.isControlDown -> withContext(Dispatchers.IO) {
                try {
                    client.eval(text).get(1, TimeUnit.SECONDS)?.parseDecimal()
                } catch (e: Exception) {
                    null
                }
            }

            else -> null
        }
        return if (precision == -1) value else value?.round(precision)
    }
}
