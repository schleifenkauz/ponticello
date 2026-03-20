package ponticello.model.score.controls

import hextant.serial.EditorRoot
import ponticello.sc.ParameterReference
import ponticello.sc.ScExpr
import ponticello.sc.allChildren
import ponticello.sc.editor.ScExprExpander
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

fun ParameterControl.getCode(): EditorRoot<ScExprExpander>? = when (this) {
    is ExprControl -> expr
    is ValueControl -> EditorRoot(ScExprExpander(value.now.toString()))
    is UGenControl -> expr
    else -> null
}

fun ScExpr.parameterReferences() = allChildren<ParameterReference>().map(ParameterReference::parameter)