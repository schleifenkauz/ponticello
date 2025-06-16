package ponticello.sc.view

import bundles.Bundle
import bundles.set
import fxutils.registerShortcuts
import hextant.codegen.ProvideImplementation
import hextant.completion.CompletionStrategy
import hextant.completion.CompoundCompleter
import hextant.context.ControlFactory
import hextant.core.editor.Expander
import hextant.core.view.ExpanderControl
import ponticello.sc.*
import ponticello.sc.editor.ReferenceCompleter
import ponticello.ui.dock.AppLayout
import ponticello.ui.misc.HelpBrowser
import reaktive.value.forEach

@ProvideImplementation(ControlFactory::class)
object ScExprExpanderControlFactory : ControlFactory<ponticello.sc.editor.ScExprExpander> {
    override fun createControl(editor: ponticello.sc.editor.ScExprExpander, arguments: Bundle): ExpanderControl {
        val completer = CompoundCompleter<Expander<*, *>, String>()
        completer.addCompleter(ponticello.sc.editor.ScExprExpander.config.completer(CompletionStrategy.simple))
        completer.addCompleter(ReferenceCompleter)
        arguments[ExpanderControl.COMPLETER] = completer
        val control = ExprExpanderControl(editor, arguments)
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
                is DecimalLiteral -> "number"
                is UnrecognizedToken -> "unrecognized"
                else -> null
            }
            if (styleClass != null) control.root.styleClass.add(styleClass)
        }
        control.userData = observer
        control.registerShortcuts {
            on("Ctrl+Alt+V") { editor.assignToVariable() }
            on("Alt+PERIOD") { editor.callMethod() }
            on("Alt+N") { editor.nameValue() }
            on("Ctrl+D") {
                val browser = editor.context[AppLayout].get<HelpBrowser>()
                browser.showClassDocumentation(editor, control)
            }
        }
        return control
    }
}