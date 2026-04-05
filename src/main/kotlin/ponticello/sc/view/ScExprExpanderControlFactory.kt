package ponticello.sc.view

import bundles.Bundle
import fxutils.bindPseudoClassState
import fxutils.registerShortcuts
import fxutils.styleClass
import hextant.codegen.ProvideImplementation
import hextant.context.ControlFactory
import hextant.core.view.ExpanderControl
import javafx.css.PseudoClass.getPseudoClass
import ponticello.model.ctx.*
import ponticello.sc.*
import ponticello.sc.editor.AbstractScExprExpander
import ponticello.sc.editor.ScExprExpander
import ponticello.ui.dock.AppLayout
import ponticello.ui.misc.HelpBrowser
import reaktive.Observer
import reaktive.and
import reaktive.value.now

@ProvideImplementation(ControlFactory::class)
object ScExprExpanderControlFactory : ControlFactory<AbstractScExprExpander<*>> {
    override fun createControl(editor: AbstractScExprExpander<*>, arguments: Bundle): ExpanderControl {
        val control = ExprExpanderControl(editor, arguments) styleClass "sc-expr"
        control.textField.styleClass("simple-sc-expr")
        val resolution = editor.identifierResolution
        val result = editor.result
        control.userData = result.observe { _, oldResult, newResult ->
            updatePseudoStyleClass(oldResult, newResult, resolution.now, resolution.now, control)
        } and editor.identifierResolution.observe { _, oldResolution, newResolution ->
            updatePseudoStyleClass(result.now, result.now, oldResolution, newResolution, control)
        } and if (editor is ScExprExpander) {
            control.bindPseudoClassState("disabled", editor.isDisabled)
        } else Observer.nothing
        val initialStyle = pseudoStyleClass(result.now, resolution.now)
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

    private fun updatePseudoStyleClass(
        oldResult: ScExpr, result: ScExpr,
        oldResolution: BoundVariable?, newResolution: BoundVariable?,
        control: ExprExpanderControl
    ) {
        val oldStyleClass = pseudoStyleClass(oldResult, oldResolution)
        val newStyleClass = pseudoStyleClass(result, newResolution)
        if (oldStyleClass == newStyleClass) return
        if (oldStyleClass != null) {
            control.textField.pseudoClassStateChanged(getPseudoClass(oldStyleClass), false)
        }
        if (newStyleClass != null) {
            control.textField.pseudoClassStateChanged(getPseudoClass(newStyleClass), true)
        }
    }

    private fun pseudoStyleClass(result: ScExpr, resolution: BoundVariable?): String? {
        val styleClass = when (result) {
            is Identifier -> when {
                result.text.first() == '~' -> "env-ref"
                result.text.first().isUpperCase() -> "class-ref"
                else -> when (resolution) {
                    is MidiVariable, is KeywordVariable, is MathConstantVariable -> "keyword"
                    is BoundIdentifier -> "variable-ref"
                    is ParameterControlVariable, is ParameterDefVariable -> "parameter-ref"
                    else -> null
                }
            }

            is BooleanLiteral, Nil -> "keyword"
            is DecimalLiteral -> "number"
            is UnrecognizedToken -> "unrecognized"
            else -> null
        }
        return styleClass
    }
}