package xenakis.sc.editor

import hextant.core.Editor
import xenakis.sc.ScExpr

interface ScExprEditor<out E: ScExpr> : Editor<E>