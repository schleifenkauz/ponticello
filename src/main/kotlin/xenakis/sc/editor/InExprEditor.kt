package xenakis.sc.editor

import hextant.context.Context
import hextant.core.editor.CompoundEditor
import reaktive.value.ReactiveValue
import reaktive.value.binding.binding
import reaktive.value.binding.flatMap
import reaktive.value.reactiveValue
import xenakis.impl.toDecimal
import xenakis.impl.zero
import xenakis.model.obj.BusObject
import xenakis.sc.DecimalLiteral
import xenakis.sc.Identifier
import xenakis.sc.ScExpr
import xenakis.sc.send

class InExprEditor(context: Context) : CompoundEditor<ScExpr>(context), BusExprEditor {
    override val busSelector: BusSelector by child(BusSelector(context))

    override val result: ReactiveValue<ScExpr> = busSelector.result.flatMap { ref ->
        val bus = ref.reference?.get<BusObject>() ?: return@flatMap reactiveValue(DecimalLiteral(zero))
        binding(bus.rate, bus.channels) { rate, channels ->
            Identifier("In").send(rate.toString(), ref, DecimalLiteral(channels.toString(), channels.toDecimal()))
        }
    }
}