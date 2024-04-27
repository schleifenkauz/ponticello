package xenakis.ui

import bundles.Bundle
import bundles.publicProperty
import bundles.set
import hextant.codegen.ProvideImplementation
import hextant.context.ControlFactory
import hextant.core.editor.ListEditor
import hextant.core.view.CompoundEditorControl
import hextant.core.view.ListEditorControl.Companion.CELL_FACTORY
import hextant.core.view.ListEditorControl.Companion.ORIENTATION
import hextant.core.view.ListEditorControl.Orientation.Horizontal
import hextant.core.view.ListEditorControl.Orientation.Vertical
import hextant.core.view.ListEditorControl.SeparatorCell
import hextant.fx.view
import org.controlsfx.glyphfont.FontAwesome
import reaktive.collection.binding.isNotEmpty
import reaktive.value.binding.equalTo
import reaktive.value.now
import xenakis.sc.EmptyExpr

val SHOW_VARIABLE_DEFAULT = publicProperty("SHOW_VARIABLE_DEFAULT", false)

val MULTILINE = publicProperty("MULTILINE_ARGUMENTS", false)

val NAMED_ARGUMENT = publicProperty("NAMED_ARGUMENT", false)

@ProvideImplementation(ControlFactory::class)
fun createControl(editor: xenakis.sc.editor.VariableEditor, arguments: Bundle): CompoundEditorControl {
    val emptyDefault = editor.defaultValue.result.equalTo(EmptyExpr)
    return compoundControl(editor, arguments, emptyDefault) {
        line {
            view(editor.name)
            if (!emptyDefault.now || arguments[SHOW_VARIABLE_DEFAULT]) {
                operator(" = ")
                view(editor.defaultValue)
                if (emptyDefault.now) {
                    children.add(button(FontAwesome.Glyph.REMOVE) {
                        arguments[SHOW_VARIABLE_DEFAULT] = false
                    })
                }
            } else {
                button(FontAwesome.Glyph.PLUS) {
                    arguments[SHOW_VARIABLE_DEFAULT] = true
                }
            }
        }
    }
}

@ProvideImplementation(ControlFactory::class)
fun createControl(editor: xenakis.sc.editor.CodeBlockEditor, arguments: Bundle): CompoundEditorControl =
    compoundControl(editor, arguments) {
        line {
            keyword("var"); space(); view(editor.variables) { set(ORIENTATION, Horizontal) }
        }
        view(editor.statements) {
            set(ORIENTATION, Vertical)
        }
    }

@ProvideImplementation(ControlFactory::class)
fun createControl(editor: xenakis.sc.editor.AccessKeyEditor, arguments: Bundle) =
    CompoundEditorControl(editor, arguments) {
        line {
            view(editor.receiver)
            operator("[")
            view(editor.keys) { set(ORIENTATION, Horizontal) }
            operator("]")
        }
    }

@ProvideImplementation(ControlFactory::class)
fun createControl(editor: xenakis.sc.editor.AssignmentEditor, arguments: Bundle) =
    CompoundEditorControl(editor, arguments) {
        line {
            view(editor.variable)
            operator(" = ")
            view(editor.expression)
        }
    }

@ProvideImplementation(ControlFactory::class)
fun createControl(editor: xenakis.sc.editor.MessageSendEditor, arguments: Bundle): CompoundEditorControl {
    val hasArguments = editor.arguments.editors.isNotEmpty()
    return compoundControl(editor, arguments, hasArguments) {
        line {
            view(editor.receiver); operator("."); view(editor.method)
            if (hasArguments.now) operator("(")
            view(editor.arguments) {
                set(ORIENTATION, Horizontal)
                set(CELL_FACTORY) { SeparatorCell(", ") }
            }
            if (hasArguments.now) operator(")")
        }
    }
}

@ProvideImplementation(ControlFactory::class)
fun createControl(editor: xenakis.sc.editor.NewObjectEditor, arguments: Bundle): CompoundEditorControl {
    val hasArguments = editor.arguments.editors.isNotEmpty()
    return compoundControl(editor, arguments) {
        line {
            keyword("new"); space(); view(editor.className)
            if (hasArguments.now) operator("(")
            view(editor.arguments)
            if (hasArguments.now) operator(")")
        }
    }
}

@ProvideImplementation(ControlFactory::class)
fun createControl(editor: xenakis.sc.editor.OperatorExprEditor, arguments: Bundle) =
    compoundControl(editor, arguments) {
        line { view(editor.left); space(); view(editor.operator); view(editor.right); space() }
    }

val SINGLE_LINE_FUNCTION = publicProperty("SINGLE_LINE_FUNCTION", false)

@ProvideImplementation(ControlFactory::class)
fun createControl(editor: xenakis.sc.editor.ScFunctionEditor, arguments: Bundle) = compoundControl(editor, arguments) {
    line {
        operator("{"); space()
        if (arguments[SINGLE_LINE_FUNCTION]) {
            operator("|")
            view(editor.parameters) { set(ORIENTATION, Horizontal) }
            operator("|")
            space()
            view(editor.body)
            operator("}")
        }
    }
    if (!arguments[SINGLE_LINE_FUNCTION]) {
        indented {
            line {
                keyword("arg")
                space()
                view(editor.parameters) { set(ORIENTATION, Horizontal) }
            }
            view(editor.body)
        }
        operator("}")
    }
}

@ProvideImplementation(ControlFactory::class)
fun createControl(editor: xenakis.sc.editor.ArgumentEditor, arguments: Bundle) = compoundControl(editor, arguments) {
    line {
        if (arguments[NAMED_ARGUMENT]) {
            view(editor.name)
            operator(": ")
        }
        view(editor.value)
    }
}

@ProvideImplementation(ControlFactory::class)
fun createControl(editor: xenakis.sc.editor.SpreadArrayEditor, arguments: Bundle) = compoundControl(editor, arguments) {
    line {
        operator("*")
        view(editor.array)
    }
}

@ProvideImplementation(ControlFactory::class)
fun createControl(editor: xenakis.sc.editor.ArrayExprEditor, arguments: Bundle) =
    compoundControl(editor, arguments) {
        layoutList(arguments, editor.elements, "[", "]")
    }

@ProvideImplementation(ControlFactory::class)
fun createControl(editor: xenakis.sc.editor.TupleElementEditor, arguments: Bundle) =
    compoundControl(editor, arguments) {
        line {
            view(editor.key)
            operator(": ")
            view(editor.value)
        }
    }

@ProvideImplementation(ControlFactory::class)
fun createControl(editor: xenakis.sc.editor.TupleExprEditor, arguments: Bundle) = compoundControl(editor, arguments) {
    layoutList(arguments, editor.elements, "(", ")")
}

@ProvideImplementation(ControlFactory::class)
fun createControl(editor: xenakis.sc.editor.LiteralArrayEditor, arguments: Bundle) =
    compoundControl(editor, arguments) {
        operator("*")
        val elements = editor.elements
        layoutList(arguments, elements, "[", "]")
    }

private fun CompoundEditorControl.Vertical.layoutList(
    arguments: Bundle,
    elements: ListEditor<*, *>,
    prefix: String,
    postfix: String
) {
    if (arguments[MULTILINE]) {
        operator(prefix)
        indented {
            view(elements) { set(ORIENTATION, Vertical) }
        }
        operator(postfix)
    } else {
        line {
            operator(prefix)
            view(elements) { set(ORIENTATION, Horizontal) }
            operator(postfix)
        }
    }
}