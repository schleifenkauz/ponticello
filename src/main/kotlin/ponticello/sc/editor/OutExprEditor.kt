package ponticello.sc.editor

import hextant.core.editor.CompoundEditor
import reaktive.value.ReactiveValue
import reaktive.value.binding.flatMap
import reaktive.value.binding.map
import reaktive.value.reactiveValue
import ponticello.impl.zero
import ponticello.sc.DecimalLiteral
import ponticello.sc.Identifier
import ponticello.sc.ScExpr
import ponticello.sc.send

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