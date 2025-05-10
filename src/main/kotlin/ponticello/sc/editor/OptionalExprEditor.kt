package ponticello.sc.editor

import hextant.core.editor.OptionalEditor
import ponticello.sc.EmptyExpr
import ponticello.sc.ScExpr

class OptionalExprEditor : OptionalEditor<ScExpr, ScExprExpander>() {
    override val default: ScExpr
        get() = EmptyExpr

    override fun createEditor(): ScExprExpander = ScExprExpander()
}