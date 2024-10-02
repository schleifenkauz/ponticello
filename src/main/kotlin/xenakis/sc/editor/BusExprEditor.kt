package xenakis.sc.editor

import xenakis.sc.ScExpr

interface BusExprEditor : ScExprEditor<ScExpr> {
    val busSelector: BusSelector
}