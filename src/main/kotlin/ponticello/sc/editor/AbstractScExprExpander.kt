package ponticello.sc.editor

import hextant.core.editor.ConfiguredExpander
import hextant.core.editor.defaultState
import ponticello.sc.EmptyExpr
import ponticello.sc.ScExpr
import reaktive.value.now

abstract class AbstractScExprExpander<E : ScExpr> : ConfiguredExpander<E, ScExprEditor<E>>(), ScExprEditor<E> {
    @Suppress("UNCHECKED_CAST")
    override fun autoExpand(text: String): Boolean = when {
        text.endsWith(".") -> {
            val objExpr = ScExprExpander(text.removeSuffix("."))
            val propertyName = IdentifierEditor("")
            autoExpandTo(PropertyAccessExprEditor(objExpr, propertyName) as ScExprEditor<E>)
        }

        text.endsWith("[") -> {
            val objExpr = ScExprExpander(text.removeSuffix("["))
            val keyExpr = ScExprExpander().defaultState()
            autoExpandTo(AccessKeyEditor(objExpr, keyExpr) as ScExprEditor<E>)
        }

        else -> false
    }

    override fun onExpansion(editor: ScExprEditor<E>) {
        when {
            editor is PropertyAccessExprEditor && editor.receiver.result.now != EmptyExpr -> {
                editor.property.notifyViews { focus() }
            }

            editor is AccessKeyEditor && editor.receiver.result.now != EmptyExpr -> {
                editor.key.notifyViews { focus() }
            }
        }
    }
}