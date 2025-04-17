package xenakis.sc.view

import bundles.Bundle
import bundles.createBundle
import bundles.publicProperty
import bundles.set
import fxutils.button
import fxutils.centerChildren
import fxutils.keyword
import fxutils.styleClass
import hextant.codegen.ProvideImplementation
import hextant.completion.NoCompleter
import hextant.context.ControlFactory
import hextant.core.view.*
import hextant.core.view.ListEditorControl.Companion.EMPTY_DISPLAY
import hextant.core.view.ListEditorControl.Companion.ORIENTATION

val MULTILINE = publicProperty("MULTILINE_ARGUMENTS", false)

val HIDE_NEW_KEYWORD = publicProperty("HIDE_NEW_KEYWORD", false)

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
            root.centerChildren()
        }
    }

@ProvideImplementation(ControlFactory::class)
fun createControl(editor: xenakis.sc.editor.AssignmentEditor, arguments: Bundle) =
    CompoundEditorControl(editor, arguments) {
        horizontal {
            styleClass("compound-expr", "assignment")
            view(editor.assignee)
            operator(" = ")
            view(editor.expression)
            root.centerChildren()
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
            root.centerChildren()
        }
    }

@ProvideImplementation(ControlFactory::class)
fun createControl(editor: xenakis.sc.editor.PropertyAccessExprEditor, arguments: Bundle) =
    CompoundEditorControl(editor, arguments) {
        horizontal {
            view(editor.receiver)
            operator(".")
            view(editor.property)
            root.centerChildren()
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
fun createControl(editor: xenakis.sc.editor.LoopExprEditor, arguments: Bundle) =
    CompoundEditorControl(editor, arguments) {
        vertical {
            styleClass("compound-expr", "loop-expr")
            keyword("loop")
            indented {
                view(editor.block)
            }
        }
    }

@ProvideImplementation(ControlFactory::class)
fun createControl(editor: xenakis.sc.editor.RunExprEditor, arguments: Bundle) =
    CompoundEditorControl(editor, arguments) {
        vertical {
            keyword("run")
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
        horizontal {
            space()
            view(editor.flow)
            space()
            keyword("rate: ")
            view(editor.rate)
            space()
            keyword("channels: ")
            view(editor.channels) {
                set(IntSpinnerControl.MIN, 1)
                set(IntSpinnerControl.MAX, 256)
            }.maxWidth = 65.0
            root.centerChildren().styleClass("bus-control-spec")
        }
    }

@ProvideImplementation(ControlFactory::class)
fun createControl(editor: xenakis.sc.editor.BufferControlSpecEditor, arguments: Bundle) =
    CompoundEditorControl(editor, arguments) {
        horizontal {
            keyword("channels: ")
            view(editor.channels) {
                set(IntSpinnerControl.MIN, 1)
                set(IntSpinnerControl.MAX, 256)
            }.maxWidth = 65.0
            root.centerChildren().styleClass("buffer-control-spec")
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
            root.centerChildren()
        }
    }

@ProvideImplementation(ControlFactory::class)
fun createControl(editor: xenakis.sc.editor.ControlSpecEditor, arguments: Bundle) =
    ChoiceEditorControl(editor, arguments).apply {
        styleClass("control-spec")
        button.isDisable = true // don't allow changing parameter types
    }

@ProvideImplementation(ControlFactory::class)
fun createControl(editor: xenakis.sc.editor.DecimalLiteralEditor, arguments: Bundle) =
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

@ProvideImplementation(ControlFactory::class)
fun createSelectorControl(editor: xenakis.sc.editor.BusSelector, arguments: Bundle) =
    ObjectSelectorControl(editor, arguments)

@ProvideImplementation(ControlFactory::class)
fun createSelectorControl(editor: xenakis.sc.editor.GroupSelector, arguments: Bundle) =
    ObjectSelectorControl(editor, arguments)

@ProvideImplementation(ControlFactory::class)
fun createControl(editor: xenakis.sc.editor.EventDictionaryEditor, arguments: Bundle) =
    CompoundEditorControl(editor, arguments) {
        vertical {
            view(editor.entries) {
                set(ORIENTATION, ListEditorControl.Orientation.Vertical)
                set(EMPTY_DISPLAY) { null }
            }
            add(button("Add entry") { editor.entries.addLast() })
        }
    }

@ProvideImplementation(ControlFactory::class)
fun createAControl(editor: xenakis.sc.editor.InExprEditor, arguments: Bundle) =
    CompoundEditorControl(editor, arguments) {
        horizontal {
            styleClass("bus-operation", "compound-expr")
            keyword("in")
            space()
            view(editor.busSelector)
        }
    }

@ProvideImplementation(ControlFactory::class)
fun createAControl(editor: xenakis.sc.editor.OutExprEditor, arguments: Bundle) =
    CompoundEditorControl(editor, arguments) {
        horizontal {
            styleClass("bus-operation", "compound-expr")
            keyword("out")
            space()
            view(editor.busSelector)
            space()
            view(editor.channelsArray)
        }
    }