package ponticello.model.score.controls

import ponticello.sc.ParameterReference
import ponticello.sc.ScExpr
import ponticello.sc.allChildren
import reaktive.value.now

fun ParameterControl.getNumericalValue() = when (this) {
    is ValueControl -> value.now
    is EnvelopeControl -> points.points.firstOrNull()?.value
    else -> null
}

fun ParameterControl.getBus() = when (this) {
    is BusControl -> bus.now
    is BusValueControl -> bus.now
    else -> null
}

fun ScExpr.parameterReferences() = allChildren<ParameterReference>().map(ParameterReference::parameter)