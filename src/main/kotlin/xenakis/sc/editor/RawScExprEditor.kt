package xenakis.sc.editor

import hextant.context.Context
import hextant.core.EditorView
import hextant.core.editor.AbstractEditor
import reaktive.value.ReactiveString
import reaktive.value.ReactiveValue
import reaktive.value.binding.map
import xenakis.sc.RawScExpr

class RawScExprEditor(
    val text: ReactiveString,
    context: Context
) : AbstractEditor<RawScExpr, EditorView>(context), ScExprEditor<RawScExpr> {
    override val result: ReactiveValue<RawScExpr> = text.map { txt -> RawScExpr(txt) }
}