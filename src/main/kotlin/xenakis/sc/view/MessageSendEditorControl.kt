package xenakis.sc.view

import bundles.Bundle
import bundles.set
import bundles.withDefault
import fxutils.centerChildren
import fxutils.registerShortcuts
import fxutils.styleClass
import hextant.codegen.ProvideImplementation
import hextant.context.ControlFactory
import hextant.core.view.CompoundEditorControl
import hextant.core.view.ListEditorControl
import hextant.core.view.ListEditorControl.Companion.ADD_WITH_COMMA
import hextant.core.view.ListEditorControl.Companion.CELL_FACTORY
import hextant.core.view.ListEditorControl.Companion.ORIENTATION
import hextant.core.view.ListEditorControl.Orientation
import hextant.core.view.ListEditorControl.SeparatorCell
import hextant.fx.view
import reaktive.collection.binding.isNotEmpty
import reaktive.value.binding.and
import reaktive.value.binding.equalTo
import reaktive.value.binding.map
import reaktive.value.now
import xenakis.sc.Identifier
import xenakis.ui.misc.HelpBrowser

class MessageSendEditorControl @ProvideImplementation(ControlFactory::class) constructor(
    private val editor: xenakis.sc.editor.MessageSendEditor,
    arguments: Bundle,
) : CompoundEditorControl(editor, arguments.withDefault(MULTILINE, HIDE_NEW_KEYWORD)) {
    private val hasArguments = editor.arguments.editors.isNotEmpty()
    val usesMethodNew = editor.receiver.result.map { receiver -> receiver is Identifier } and
            editor.method.result.equalTo(Identifier("new"))

    init {
        triggerLayoutOnChange(hasArguments)
        triggerLayoutOnChange(usesMethodNew)
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
                view(editor.arguments, cached = false) {
                    set(ORIENTATION, Orientation.Horizontal)
                    set(CELL_FACTORY) { SeparatorCell(", ").also { it.root.centerChildren() } }
                    set(ADD_WITH_COMMA, true)
                }.root.centerChildren().styleClass("compound-expr", "arguments")
            }
        }
        if (arguments[MULTILINE] && hasArguments.now) {
            styleClass("compound-expr", "message-send")
            indented {
                view(editor.arguments, cached = false) {
                    set(ORIENTATION, Orientation.Vertical)
                    set(CELL_FACTORY) { ListEditorControl.DefaultCell() }
                }
            }
        }
    }
}
