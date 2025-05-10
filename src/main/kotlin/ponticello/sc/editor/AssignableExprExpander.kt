package ponticello.sc.editor

import hextant.core.editor.Expander
import hextant.core.editor.defaultState
import reaktive.value.now
import ponticello.sc.AssignableScExpr
import ponticello.sc.EmptyExpr
import ponticello.sc.Identifier
import ponticello.sc.UnrecognizedToken

class AssignableExprExpander(

) : Expander<AssignableScExpr, ScExprEditor<AssignableScExpr>>(), ScExprEditor<AssignableScExpr> {
    constructor(text: String) : this() {
        setInitialText(text)
    }

    override fun defaultResult(): AssignableScExpr = EmptyExpr

    override fun autoExpand(text: String): Boolean = when {
        text.endsWith(".") -> {
            val objExpr = ScExprExpander(text.removeSuffix("."))
            val propertyName = IdentifierEditor("")
            autoExpandTo(PropertyAccessExprEditor(objExpr, propertyName))
        }
        text.endsWith("[") -> {
            val objExpr = ScExprExpander(text.removeSuffix("["))
            val keyExpr = ScExprExpander().defaultState()
            autoExpandTo(AccessKeyEditor(objExpr, keyExpr))
        }
        else -> false
    }

    override fun onExpansion(editor: ScExprEditor<AssignableScExpr>) {
        when {
            editor is PropertyAccessExprEditor && editor.receiver.result.now != EmptyExpr -> {
                editor.property.notifyViews { focus() }
            }
            editor is AccessKeyEditor && editor.receiver.result.now != EmptyExpr -> {
                editor.key.notifyViews { focus() }
            }
        }
    }

    override fun compile(token: String): AssignableScExpr = when {
        Identifier.isValid(token) -> Identifier(token)
        token == "" -> EmptyExpr
        else -> UnrecognizedToken(token)
    }
}