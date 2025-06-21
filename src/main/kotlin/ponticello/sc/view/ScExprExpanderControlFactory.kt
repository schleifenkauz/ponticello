package ponticello.sc.view

import bundles.Bundle
import bundles.set
import fxutils.bindPseudoClassState
import fxutils.registerShortcuts
import fxutils.styleClass
import hextant.codegen.ProvideImplementation
import hextant.completion.CompletionStrategy
import hextant.completion.CompoundCompleter
import hextant.context.ControlFactory
import hextant.core.editor.Expander
import hextant.core.view.ExpanderControl
import javafx.css.PseudoClass.getPseudoClass
import ponticello.sc.*
import ponticello.sc.editor.ReferenceCompleter
import ponticello.ui.dock.AppLayout
import ponticello.ui.misc.HelpBrowser
import reaktive.and
import reaktive.value.now

@ProvideImplementation(ControlFactory::class)
object ScExprExpanderControlFactory : ControlFactory<ponticello.sc.editor.ScExprExpander> {
    override fun createControl(editor: ponticello.sc.editor.ScExprExpander, arguments: Bundle): ExpanderControl {
        val completer = CompoundCompleter<Expander<*, *>, String>()
        completer.addCompleter(ponticello.sc.editor.ScExprExpander.config.completer(CompletionStrategy.simple))
        completer.addCompleter(ReferenceCompleter)
        arguments[ExpanderControl.COMPLETER] = completer
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
            on("Ctrl+Alt+V") { editor.assignToVariable() }
            on("Alt+PERIOD") { editor.callMethod() }
            on("Alt+F") { editor.toggleDisabled() }
            on("Alt+N") { editor.nameValue() }
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