package xenakis.ui

import bundles.Bundle
import bundles.publicProperty
import bundles.set
import hextant.codegen.ProvideImplementation
import hextant.completion.CompletionStrategy
import hextant.completion.CompoundCompleter
import hextant.completion.NoCompleter
import hextant.context.ControlFactory
import hextant.core.editor.ListEditor
import hextant.core.view.CompoundEditorControl
import hextant.core.view.ExpanderControl
import hextant.core.view.ExpanderControl.Companion.COMPLETER
import hextant.core.view.ListEditorControl.Companion.CELL_FACTORY
import hextant.core.view.ListEditorControl.Companion.ORIENTATION
import hextant.core.view.ListEditorControl.Orientation.Horizontal
import hextant.core.view.ListEditorControl.Orientation.Vertical
import hextant.core.view.ListEditorControl.SeparatorCell
import hextant.core.view.TokenEditorControl
import hextant.fx.registerShortcuts
import hextant.fx.view
import org.controlsfx.glyphfont.FontAwesome
import reaktive.collection.binding.isNotEmpty
import reaktive.value.binding.equalTo
import reaktive.value.forEach
import reaktive.value.now
import xenakis.sc.*
import xenakis.sc.editor.ReferenceCompleter

val SHOW_VARIABLE_DEFAULT = publicProperty("SHOW_VARIABLE_DEFAULT", false)
val SHOW_PARAMETER_DEFAULT = publicProperty("SHOW_PARAMETER_DEFAULT", false)

val SHOW_EMPTY_VARIABLES = publicProperty("SHOW_VARIABLE_DEFAULT", false)

val MULTILINE = publicProperty("MULTILINE_ARGUMENTS", false)

val NAMED_ARGUMENT = publicProperty("NAMED_ARGUMENT", false)

val DISPLAY_BRACES = publicProperty("DISPLAY_BRACES", true)

@ProvideImplementation(ControlFactory::class)
fun createControl(editor: xenakis.sc.editor.VariableEditor, arguments: Bundle): CompoundEditorControl {
    val emptyDefault = editor.defaultValue.result.equalTo(EmptyExpr)
    return compoundControl(editor, arguments, emptyDefault) {
        styleClass("variable")
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
fun createControl(editor: xenakis.sc.editor.ParameterEditor, arguments: Bundle): CompoundEditorControl {
    val emptyDefault = editor.defaultValue.result.equalTo(EmptyExpr)
    return compoundControl(editor, arguments, emptyDefault) {
        styleClass("parameter")
        line {
            view(editor.name)
            if (!emptyDefault.now || arguments[SHOW_PARAMETER_DEFAULT]) {
                operator(" = ")
                view(editor.defaultValue)
                if (emptyDefault.now) {
                    children.add(button(FontAwesome.Glyph.REMOVE) {
                        arguments[SHOW_PARAMETER_DEFAULT] = false
                    })
                }
            } else {
                button(FontAwesome.Glyph.PLUS) {
                    arguments[SHOW_PARAMETER_DEFAULT] = true
                }
            }
        }
    }
}

@ProvideImplementation(ControlFactory::class)
fun createControl(editor: xenakis.sc.editor.CodeBlockEditor, arguments: Bundle): CompoundEditorControl {
    val anyVariables = editor.variables.editors.isNotEmpty()
    return compoundControl(editor, arguments, anyVariables) {
        styleClass("code-block")
        if (anyVariables.now || arguments[SHOW_EMPTY_VARIABLES]) {
            line {
                keyword("var"); space(); view(editor.variables) {
                set(ORIENTATION, Horizontal)
                set(CELL_FACTORY) { SeparatorCell(", ") }
            }
            }
        }
        view(editor.statements) {
            set(ORIENTATION, Vertical)
        }
    }
}

@ProvideImplementation(ControlFactory::class)
fun createControl(editor: xenakis.sc.editor.CodeGroupEditor, arguments: Bundle): CompoundEditorControl =
    compoundControl(editor, arguments) {
        styleClass("code-group")
        operator("(")
        indented {
            view(editor.block)
        }
        operator(")")
    }

@ProvideImplementation(ControlFactory::class)
fun createControl(editor: xenakis.sc.editor.AccessKeyEditor, arguments: Bundle) =
    CompoundEditorControl(editor, arguments) {
        styleClass("access-key")
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
        styleClass("assignment")
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
        styleClass("message-send")
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
        styleClass("new-object")
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
        styleClass("operator-expr")
        line { view(editor.left); space(); view(editor.operator); space(); view(editor.right); }
    }

val SINGLE_LINE_FUNCTION = publicProperty("SINGLE_LINE_FUNCTION", false)

@ProvideImplementation(ControlFactory::class)
fun createControl(editor: xenakis.sc.editor.ScFunctionEditor, arguments: Bundle): CompoundEditorControl {
    val anyParameters = editor.parameters.editors.isNotEmpty()
    return compoundControl(editor, arguments, anyParameters) {
        styleClass("function")
        line {
            if (arguments[DISPLAY_BRACES]) {
                operator("{")
                space()
            }
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
                if (anyParameters.now) {
                    line {
                        keyword("arg")
                        space()
                        view(editor.parameters) {
                            set(ORIENTATION, Horizontal)
                            set(CELL_FACTORY) { SeparatorCell(", ") }
                        }
                    }
                }
                view(editor.body)
            }
            if (arguments[DISPLAY_BRACES]) {
                operator("}")
            }
        }
    }
}

@ProvideImplementation(ControlFactory::class)
fun createControl(editor: xenakis.sc.editor.ArgumentEditor, arguments: Bundle) = compoundControl(editor, arguments) {
    styleClass("argument")
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
    styleClass("spread-array")
    line {
        operator("*")
        view(editor.array)
    }
}

@ProvideImplementation(ControlFactory::class)
fun createControl(editor: xenakis.sc.editor.ArrayExprEditor, arguments: Bundle) = compoundControl(editor, arguments) {
    styleClass("array")
    layoutList(arguments, editor.elements, "[", "]")
}

@ProvideImplementation(ControlFactory::class)
fun createControl(editor: xenakis.sc.editor.TupleElementEditor, arguments: Bundle) =
    compoundControl(editor, arguments) {
        styleClass("tuple-element")
        line {
            view(editor.key)
            operator(": ")
            view(editor.value)
        }
    }

@ProvideImplementation(ControlFactory::class)
fun createControl(editor: xenakis.sc.editor.TupleExprEditor, arguments: Bundle) = compoundControl(editor, arguments) {
    styleClass("tuple")
    layoutList(arguments, editor.elements, "(", ")")
}

@ProvideImplementation(ControlFactory::class)
fun createControl(editor: xenakis.sc.editor.LiteralArrayEditor, arguments: Bundle) =
    compoundControl(editor, arguments) {
        styleClass("literal-array")
        operator("*")
        val elements = editor.elements
        layoutList(arguments, elements, "[", "]")
    }

private fun CompoundEditorControl.Vertical.layoutList(
    arguments: Bundle, elements: ListEditor<*, *>, prefix: String, postfix: String
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

@ProvideImplementation(ControlFactory::class)
object ScExprExpanderControlFactory: ControlFactory<xenakis.sc.editor.ScExprExpander> {
    override fun createControl(editor: xenakis.sc.editor.ScExprExpander, arguments: Bundle): ExpanderControl {
        val completer = CompoundCompleter<Any, String>()
        completer.addCompleter(xenakis.sc.editor.ScExprExpander.config.completer(CompletionStrategy.simple))
        completer.addCompleter(ReferenceCompleter)
        arguments[COMPLETER] = completer
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
        }
        return control
    }
}

@ProvideImplementation(ControlFactory::class)
fun createControl(editor: xenakis.sc.editor.IfExprEditor, arguments: Bundle) = compoundControl(editor, arguments) {
    line {
        keyword("if")
        space()
        view(editor.condition)
    }
    indented {
        view(editor.then)
    }
    keyword("else")
    indented {
        view(editor.otherwise)
    }
}

@ProvideImplementation(ControlFactory::class)
fun createControl(editor: xenakis.sc.editor.WhileExprEditor, arguments: Bundle) = compoundControl(editor, arguments) {
    line {
        keyword("while")
        space()
        view(editor.condition)
    }
    indented {
        view(editor.block)
    }
}

@ProvideImplementation(ControlFactory::class)
fun createControl(editor: xenakis.sc.editor.OperatorEditor, arguments: Bundle): TokenEditorControl =
    TokenEditorControl(editor, arguments, completer = NoCompleter, styleClass = "operator")