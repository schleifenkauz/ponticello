package ponticello.sc.editor

import hextant.core.EditorView
import hextant.core.editor.AbstractEditor
import reaktive.value.ReactiveString
import reaktive.value.ReactiveValue
import reaktive.value.binding.map
import ponticello.sc.RawScExpr

class RawScExprEditor(
    val text: ReactiveString,
) : AbstractEditor<RawScExpr, EditorView>(), ScExprEditor<RawScExpr> {
    override val result: ReactiveValue<RawScExpr> = text.map { txt -> RawScExpr(txt) }
}