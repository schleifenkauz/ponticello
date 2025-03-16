package xenakis.sc.editor

import hextant.core.editor.OptionalEditor
import xenakis.sc.EmptyExpr
import xenakis.sc.ScExpr

class OptionalExprEditor : OptionalEditor<ScExpr, ScExprExpander>() {
    override val default: ScExpr
        get() = EmptyExpr

    override fun createEditor(): ScExprExpander = ScExprExpander()
}