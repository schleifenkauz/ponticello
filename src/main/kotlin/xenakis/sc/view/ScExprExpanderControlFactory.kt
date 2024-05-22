package xenakis.sc.view

import bundles.Bundle
import bundles.set
import hextant.codegen.ProvideImplementation
import hextant.completion.CompletionStrategy
import hextant.completion.CompoundCompleter
import hextant.context.ControlFactory
import hextant.core.view.ExpanderControl
import hextant.fx.registerShortcuts
import reaktive.value.forEach
import xenakis.sc.*
import xenakis.sc.editor.ReferenceCompleter
import xenakis.ui.HelpBrowser

@ProvideImplementation(ControlFactory::class)
object ScExprExpanderControlFactory : ControlFactory<xenakis.sc.editor.ScExprExpander> {
    override fun createControl(editor: xenakis.sc.editor.ScExprExpander, arguments: Bundle): ExpanderControl {
        val completer = CompoundCompleter<Any, String>()
        completer.addCompleter(xenakis.sc.editor.ScExprExpander.config.completer(CompletionStrategy.simple))
        completer.addCompleter(ReferenceCompleter)
        arguments[ExpanderControl.COMPLETER] = completer
        val control = ExpanderControl(editor, arguments)
        val possibleStyleClasses = listOf("identifier", "keyword", "number", "unrecognized")
        val observer = editor.result.forEach { result ->
            control.root.styleClass.removeAll(possibleStyleClasses)
            val styleClass = when (result) {
                is Identifier -> when {
                    result.text.first() == '~' -> "env-ref"
                    result.text.first().isUpperCase() -> "class-ref"
                    else -> "variable-ref"
                }

                is BooleanLiteral, Nil -> "keyword"
                is DoubleLiteral, is IntegerLiteral -> "number"
                is UnrecognizedToken -> "unrecognized"
                else -> null
            }
            if (styleClass != null) control.root.styleClass.add(styleClass)
        }
        control.userData = observer
        control.registerShortcuts {
            on("Ctrl+Shift+DIGIT0") { editor.assignToVariable() }
            on("Ctrl+PERIOD") { editor.callMethod() }
            on("Ctrl+Shift+PERIOD") { editor.nameValue() }
            on("Ctrl+D") {
                val browser = editor.context[HelpBrowser]
                val bounds = control.localToScreen(control.boundsInLocal)
                browser.showClassDocumentation(editor, bounds)
            }
        }
        return control
    }
}