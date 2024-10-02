package xenakis.sc.editor

import hextant.context.Context
import hextant.core.editor.CompoundEditor
import reaktive.value.ReactiveValue
import reaktive.value.binding.binding
import reaktive.value.binding.flatMap
import reaktive.value.reactiveValue
import xenakis.model.BusObject
import xenakis.sc.DoubleLiteral
import xenakis.sc.Identifier
import xenakis.sc.ScExpr
import xenakis.sc.send

class OutExprEditor(
    context: Context,
    busSelector: BusSelector = BusSelector(context),
    channelsArray: ScExprExpander = ScExprExpander(context)
) : CompoundEditor<ScExpr>(context), BusExprEditor {
    override val busSelector by child(busSelector)
    val channelsArray by child(channelsArray)

    override val result: ReactiveValue<ScExpr> = busSelector.result.flatMap { ref ->
        val bus = ref.reference?.get<BusObject>() ?: return@flatMap reactiveValue(DoubleLiteral(0.0))
        binding(bus.rate, channelsArray.result) { rate, snd ->
            Identifier("Out").send(rate.toString(), ref, snd)
        }
    }
}