package ponticello.sc.view

import bundles.Bundle
import fxutils.centerChildren
import fxutils.registerShortcuts
import fxutils.styleClass
import hextant.codegen.ProvideImplementation
import hextant.context.ControlFactory
import hextant.core.view.CompoundEditorControl
import reaktive.collection.binding.isNotEmpty
import reaktive.value.binding.and
import reaktive.value.binding.equalTo
import reaktive.value.binding.map
import reaktive.value.now
import ponticello.sc.Identifier
import ponticello.ui.misc.HelpBrowser

class MessageSendEditorControl @ProvideImplementation(ControlFactory::class) constructor(
    private val editor: ponticello.sc.editor.MessageSendEditor,
    arguments: Bundle,
) : CompoundEditorControl(editor, arguments) {
    private val hasArguments = editor.arguments.editors.isNotEmpty()
    val usesMethodNew = editor.receiver.result.map { receiver -> receiver is Identifier } and
            editor.method.result.equalTo(Identifier("new"))

    init {
        triggerLayoutOnChange(hasArguments)
        triggerLayoutOnChange(usesMethodNew)
        supportArguments(MULTILINE, HIDE_NEW_KEYWORD)
    }

    override fun build(): Layout = vertical {
        horizontal {
            view(editor.receiver)
            if (!(usesMethodNew.now && arguments[HIDE_NEW_KEYWORD])) {
                operator(".")
                view(editor.method) {
                    registerShortcuts {
                        on("Ctrl+D") {
                            val bounds = localToScreen(boundsInLocal)
                            editor.context[HelpBrowser].showMethodDocumentation(editor.method, bounds)
                        }
                    }
                }
            }
            if (!arguments[MULTILINE] || !hasArguments.now) {
                styleClass("compound-expr", "message-send")
                space()
                viewHorizontal(editor.arguments).root.centerChildren().styleClass("compound-expr", "arguments")
            }
        }
        if (arguments[MULTILINE] && hasArguments.now) {
            styleClass("compound-expr", "message-send")
            indented {
                viewVertical(editor.arguments)
            }
        }
    }
}
