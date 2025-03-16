package xenakis.sc.editor

import hextant.core.editor.CompoundEditor
import reaktive.value.ReactiveValue
import reaktive.value.binding.binding
import reaktive.value.binding.flatMap
import reaktive.value.binding.map
import reaktive.value.reactiveValue
import xenakis.impl.zero
import xenakis.sc.DecimalLiteral
import xenakis.sc.Identifier
import xenakis.sc.ScExpr
import xenakis.sc.send

class OutExprEditor(
    override val busSelector: BusSelector = BusSelector(),
    val channelsArray: ScExprExpander = ScExprExpander()
) : CompoundEditor<ScExpr>(), BusExprEditor {



    override val result: ReactiveValue<ScExpr> = busSelector.result.flatMap { ref ->
        val bus = ref.get() ?: return@flatMap reactiveValue(DecimalLiteral(zero))
        channelsArray.result.map { snd ->
            Identifier("Out").send(bus.rate.toString(), ref, snd)
        }
    }
}