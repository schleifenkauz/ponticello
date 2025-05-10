package ponticello.sc.editor

import hextant.core.Editor
import ponticello.sc.ScExpr

interface ScExprEditor<out E : ScExpr> : Editor<E>