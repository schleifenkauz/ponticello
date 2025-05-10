package ponticello.sc.editor

import ponticello.sc.ScExpr

interface BusExprEditor : ScExprEditor<ScExpr> {
    val busSelector: BusSelector
}