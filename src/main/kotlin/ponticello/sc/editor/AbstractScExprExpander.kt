package ponticello.sc.editor

import bundles.getOrNull
import hextant.core.editor.ConfiguredExpander
import hextant.core.editor.defaultState
import ponticello.model.ctx.BoundVariable
import ponticello.model.ctx.Scope
import ponticello.sc.EmptyExpr
import ponticello.sc.ScExpr
import reaktive.Observer
import reaktive.value.ReactiveValue
import reaktive.value.now
import reaktive.value.reactiveValue

abstract class AbstractScExprExpander<E : ScExpr> : ConfiguredExpander<E, ScExprEditor<E>>(), ScExprEditor<E> {
    lateinit var identifierResolution: ReactiveValue<BoundVariable?>
        private set

    private var variableDefinitionRenamer: Observer? = null
    var associatedDefinition: IdentifierEditor? = null
        private set

    override fun doInitialize() {
        super.doInitialize()
        identifierResolution = context.getOrNull(Scope)?.resolve(text) ?: reactiveValue(null)
        val definition = associatedDefinition
        if (definition != null) {
            variableDefinitionRenamer = text.observe { _, _, new ->
                if (new != null) {
                    definition.setText(new)
                }
            }
        }
    }

    fun bindToDefinition(identifier: IdentifierEditor) {
        associatedDefinition = identifier
    }

    fun unbindDefinition() {
        variableDefinitionRenamer?.kill()
        variableDefinitionRenamer = null
        associatedDefinition = null
    }

    @Suppress("UNCHECKED_CAST")
    override fun autoExpand(text: String): Boolean = when {
        text.endsWith(".") && text.dropLast(1).toIntOrNull() == null -> {
            val objExpr = ScExprExpander(text.removeSuffix("."))
            val propertyName = IdentifierEditor("")
            autoExpandTo(PropertyAccessExprEditor(objExpr, propertyName) as ScExprEditor<E>)
        }

        text.endsWith("[") -> {
            val objExpr = ScExprExpander(text.removeSuffix("["))
            val keyExpr = ScExprExpander().defaultState()
            autoExpandTo(AccessKeyEditor(objExpr, keyExpr) as ScExprEditor<E>)
        }

        else -> super.autoExpand(text)
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