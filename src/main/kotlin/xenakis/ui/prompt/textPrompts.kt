package xenakis.ui.prompt

import hextant.fx.registerShortcuts
import javafx.scene.input.KeyEvent
import xenakis.impl.*
import xenakis.model.ObjectRegistry
import xenakis.sc.Identifier

class PredicateTextPrompt(
    title: String, initialText: String, private val check: (String) -> Boolean
) : TextPrompt<String>(title, initialText) {
    override fun convert(text: String): String? = text.takeIf(check)
}

class SimpleTextPrompt(title: String, initialText: String) : TextPrompt<String>(title, initialText) {
    override fun convert(text: String): String = text
}

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

class IntegerPrompt(
    title: String, initialValue: Int?,
    private val range: IntRange = Int.MIN_VALUE..Int.MAX_VALUE
) : TextPrompt<Int>(title, initialValue?.toString().orEmpty()) {
    init {
        content.registerShortcuts(KeyEvent.KEY_PRESSED) {
            on("DOWN") {
                content.text.toIntOrNull()?.let { v -> if (v - 1 in range) content.text = (v - 1).toString() }
            }
            on("UP") { content.text.toIntOrNull()?.let { v -> if (v + 1 in range) content.text = (v + 1).toString() } }
        }
    }

    override fun convert(text: String): Int? = text.toIntOrNull()?.takeIf { v -> v in range }
}

class NamePrompt(
    private val registry: ObjectRegistry<*>, title: String, initialName: String
) : TextPrompt<String>(title, initialName) {
    override fun convert(text: String): String? {
        if (!Identifier.isValid(text)) return null
        if (registry.has(text)) return null
        return text
    }
}