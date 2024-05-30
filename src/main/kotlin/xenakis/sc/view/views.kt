package xenakis.sc.view

import bundles.Bundle
import bundles.createBundle
import bundles.publicProperty
import bundles.set
import hextant.codegen.ProvideImplementation
import hextant.completion.NoCompleter
import hextant.context.ControlFactory
import hextant.core.view.ChoiceEditorControl
import hextant.core.view.CompoundEditorControl
import hextant.core.view.OptionalEditorControl
import hextant.core.view.TokenEditorControl
import hextant.fx.keyword
import xenakis.ui.centerChildrenVertically
import xenakis.ui.styleClass

val MULTILINE = publicProperty("MULTILINE_ARGUMENTS", false)

val DISPLAY_BRACES = publicProperty("DISPLAY_BRACES", true)

@ProvideImplementation(ControlFactory::class)
fun createControl(editor: xenakis.sc.editor.AccessKeyEditor, arguments: Bundle) =
    CompoundEditorControl(editor, arguments) {
        horizontal {
            styleClass("access-key")
            view(editor.receiver)
            operator("[")
            view(editor.key)
            operator("]")
            root.centerChildrenVertically()
        }
    }

@ProvideImplementation(ControlFactory::class)
fun createControl(editor: xenakis.sc.editor.AssignmentEditor, arguments: Bundle) =
    CompoundEditorControl(editor, arguments) {
        horizontal {
            styleClass("compound-expr", "assignment")
            view(editor.variable)
            operator(" = ")
            view(editor.expression)
            root.centerChildrenVertically()
        }
    }

@ProvideImplementation(ControlFactory::class)
fun createControl(editor: xenakis.sc.editor.OperatorExprEditor, arguments: Bundle) =
    CompoundEditorControl(editor, arguments) {
        horizontal {
            styleClass("compound-expr", "operator-expr")
            view(editor.left)
            space()
            view(editor.operator)
            space()
            view(editor.right)
            root.centerChildrenVertically()
        }
    }

@ProvideImplementation(ControlFactory::class)
fun createControl(editor: xenakis.sc.editor.SpreadArrayEditor, arguments: Bundle) =
    CompoundEditorControl(editor, arguments) {
        horizontal {
            styleClass("spread-array")
            operator("*")
            view(editor.array)
        }
    }

@ProvideImplementation(ControlFactory::class)
fun createControl(editor: xenakis.sc.editor.NamedExprEditor, arguments: Bundle) =
    CompoundEditorControl(editor, arguments) {
        vertical {
            styleClass("compound-expr", "named-expr")
            horizontal {
                view(editor.name)
                operator(": ")
                view(editor.value)
            }
        }
    }

@ProvideImplementation(ControlFactory::class)
fun createControl(editor: xenakis.sc.editor.IfExprEditor, arguments: Bundle) =
    CompoundEditorControl(editor, arguments) {
        vertical {
            styleClass("compound-expr", "if")
            horizontal {
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
    }

@ProvideImplementation(ControlFactory::class)
fun createControl(editor: xenakis.sc.editor.WhileExprEditor, arguments: Bundle) =
    CompoundEditorControl(editor, arguments) {
        vertical {
            styleClass("compound-expr", "while")
            horizontal {
                keyword("while")
                space()
                view(editor.condition)
            }
            indented {
                view(editor.block)
            }
        }
    }

@ProvideImplementation(ControlFactory::class)
fun createControl(editor: xenakis.sc.editor.OperatorEditor, arguments: Bundle): TokenEditorControl =
    TokenEditorControl(editor, arguments, completer = NoCompleter, styleClass = "operator")

@ProvideImplementation(ControlFactory::class)
fun createControl(editor: xenakis.sc.editor.BusControlSpecEditor, arguments: Bundle) =
    CompoundEditorControl(editor, arguments) {
        vertical {
            horizontal {
                operator(" = ")
                view(editor.defaultValue)
            }
        }
    }

@ProvideImplementation(ControlFactory::class)
fun createControl(editor: xenakis.sc.editor.BufferControlSpecEditor, arguments: Bundle) =
    CompoundEditorControl(editor, arguments) {
        vertical {
            horizontal {
                operator(" = ")
                view(editor.defaultValue)
            }
        }
    }

@ProvideImplementation(ControlFactory::class)
fun createControl(editor: xenakis.sc.editor.NumericalControlSpecEditor, arguments: Bundle) =
    CompoundEditorControl(editor, arguments) {
        horizontal {
            operator(" = ")
            view(editor.defaultValue)//.minWidth = 30.0
            space()
            operator("(")
            view(editor.min)//.minWidth = 30.0
            operator("..")
            view(editor.max)//.minWidth = 30.0
            operator(")")
            space()
            keyword("step: ")
            view(editor.step)//.minWidth = 30.0
            space()
            keyword("warp: ")
            view(editor.warp)
            space()
            keyword("color: ")
            view(editor.associatedColor)//.minWidth = 30.0
            styleClass("numerical-control-spec")
            root.centerChildrenVertically()
        }
    }

@ProvideImplementation(ControlFactory::class)
fun createControl(editor: xenakis.sc.editor.ControlSpecEditor, arguments: Bundle) =
    ChoiceEditorControl(editor, arguments).styleClass("control-spec")

@ProvideImplementation(ControlFactory::class)
fun createControl(editor: xenakis.sc.editor.DoubleLiteralEditor, arguments: Bundle) =
    TokenEditorControl(editor, arguments, styleClass = "number")

@ProvideImplementation(ControlFactory::class)
fun createControl(editor: xenakis.sc.editor.OptionalExprEditor, arguments: Bundle): OptionalEditorControl {
    arguments[OptionalEditorControl.EMPTY_DISPLAY] = { keyword("nil") }
    return OptionalEditorControl(editor, arguments)
}

@ProvideImplementation(ControlFactory::class)
fun createControl(editor: xenakis.sc.editor.SymbolLiteralEditor, arguments: Bundle) =
    CompoundEditorControl(editor, arguments) {
        horizontal {
            operator("'")
            TokenEditorControl(editor, createBundle(), styleClass = "symbol")
            operator("'")
        }
    }

@ProvideImplementation(ControlFactory::class)
fun createControl(editor: xenakis.sc.editor.StringLiteralEditor, arguments: Bundle) =
    CompoundEditorControl(editor, arguments) {
        horizontal {
            operator("\"")
            TokenEditorControl(editor, createBundle(), styleClass = "string")
            operator("\"")
        }
    }
