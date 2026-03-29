package ponticello.sc.view

import bundles.Bundle
import fxutils.bindPseudoClassState
import fxutils.registerShortcuts
import fxutils.styleClass
import hextant.codegen.ProvideImplementation
import hextant.context.ControlFactory
import hextant.core.view.ExpanderControl
import javafx.css.PseudoClass.getPseudoClass
import ponticello.sc.*
import ponticello.sc.editor.ScExprExpander
import ponticello.ui.dock.AppLayout
import ponticello.ui.misc.HelpBrowser
import reaktive.and
import reaktive.value.now

@ProvideImplementation(ControlFactory::class)
object ScExprExpanderControlFactory : ControlFactory<ScExprExpander> {
    override fun createControl(editor: ScExprExpander, arguments: Bundle): ExpanderControl {
        val control = ExprExpanderControl(editor, arguments) styleClass "sc-expr"
        control.textField.styleClass("simple-sc-expr")
        control.userData = editor.result.observe { _, oldResult, result ->
            val oldStyleClass = pseudoStyleClass(oldResult)
            val newStyleClass = pseudoStyleClass(result)
            if (oldStyleClass == newStyleClass) return@observe
            if (oldStyleClass != null) {
                control.textField.pseudoClassStateChanged(getPseudoClass(oldStyleClass), false)
            }
            if (newStyleClass != null) {
                control.textField.pseudoClassStateChanged(getPseudoClass(newStyleClass), true)
            }
        } and control.bindPseudoClassState("disabled", editor.isDisabled)
        val initialStyle = pseudoStyleClass(editor.result.now)
        if (initialStyle != null) {
            control.textField.pseudoClassStateChanged(getPseudoClass(initialStyle), true)
        }
        control.registerShortcuts {
            on("Ctrl+D") {
                val browser = editor.context[AppLayout].get<HelpBrowser>()
                browser.showClassDocumentation(editor, control)
            }
        }
        return control
    }

    private fun pseudoStyleClass(result: ScExpr): String? {
        val styleClass = when (result) {
            is Identifier -> when {
                result.text.first() == '~' -> "env-ref"
                result.text.first().isUpperCase() -> "class-ref"
                else -> "variable-ref"
            }

            is BooleanLiteral, Nil -> "keyword"
            is DecimalLiteral -> "number"
            is UnrecognizedToken -> "unrecognized"
            else -> null
        }
        return styleClass
    }
}