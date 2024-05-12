package xenakis.sc.editor

import hextant.context.Context
import hextant.core.editor.OptionalEditor
import xenakis.sc.EmptyExpr
import xenakis.sc.ScExpr

class OptionalExprEditor(context: Context) : OptionalEditor<ScExpr, ScExprExpander>(context) {
    override val default: ScExpr
        get() = EmptyExpr

    override fun createEditor(): ScExprExpander = ScExprExpander(context)
}