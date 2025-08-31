package ponticello.ui.misc

import fxutils.centerChildren
import fxutils.controls.IntSpinner
import fxutils.prompt.ConfirmablePrompt
import javafx.scene.Node
import javafx.scene.control.Label
import javafx.scene.layout.HBox
import ponticello.impl.*
import reaktive.value.ReactiveVariable
import reaktive.value.now
import reaktive.value.reactiveVariable

class TempoSyncPrompt private constructor(
    private val gridBpm: Decimal,
    private val trackBpm: Decimal,
    private var quotient: ReactiveVariable<Int> = reactiveVariable(1),
    private var dividend: ReactiveVariable<Int> = reactiveVariable(1),
) : ConfirmablePrompt<Decimal>("Sync tempo?", cancelText = "_No", confirmText = "_Yes") {

    override val content: Node = HBox(
        Label("Tempo ratio: "),
        IntSpinner(quotient, min = 1, max = 12),
        Label("/"),
        IntSpinner(dividend, min = 1, max = 12)
    ).centerChildren()

    override fun confirm(): Decimal {
        val ratio = quotient.now.toDecimal().withPrecision(4) / dividend.now
        return (gridBpm.withPrecision(4) / trackBpm) * ratio
    }

    companion object {
        fun create(gridBpm: Decimal, trackBpm: Decimal): TempoSyncPrompt {
            val (quotient, dividend) = when {
                gridBpm / trackBpm > 1.5.toDecimal() -> Pair(1, (gridBpm / trackBpm).roundToInt())
                trackBpm / gridBpm > 1.5.toDecimal() -> Pair((trackBpm / gridBpm).roundToInt(), 1)
                else -> Pair(1, 1)
            }
            return TempoSyncPrompt(
                gridBpm, trackBpm,
                reactiveVariable(quotient), reactiveVariable(dividend)
            )
        }
    }
}