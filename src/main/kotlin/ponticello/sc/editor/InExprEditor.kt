package ponticello.sc.editor

import hextant.core.editor.CompoundEditor
import reaktive.value.ReactiveValue
import reaktive.value.binding.flatMap
import reaktive.value.binding.map
import reaktive.value.reactiveValue
import ponticello.impl.toDecimal
import ponticello.impl.zero
import ponticello.sc.DecimalLiteral
import ponticello.sc.Identifier
import ponticello.sc.ScExpr
import ponticello.sc.send

class InExprEditor : CompoundEditor<ScExpr>(), BusExprEditor {
    override val busSelector: BusSelector by child(BusSelector())

    override lateinit var result: ReactiveValue<ScExpr>
        private set

    override fun doInitialize() {
        super.doInitialize()
        result = busSelector.result.flatMap { ref ->
            val bus = ref.get() ?: return@flatMap reactiveValue(DecimalLiteral(zero))
            bus.channels.map { channels ->
                Identifier("In").send(bus.rate.toString(), ref, DecimalLiteral(channels.toString(), channels.toDecimal()))
            }
        }
    }
}