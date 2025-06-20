package ponticello.sc.view

import bundles.Bundle
import bundles.createBundle
import bundles.publicProperty
import bundles.set
import fxutils.button
import fxutils.centerChildren
import fxutils.keyword
import fxutils.styleClass
import hextant.completion.NoCompleter
import hextant.core.view.*
import hextant.core.view.ListEditorControl.Companion.ADD_WITH_COMMA
import hextant.core.view.ListEditorControl.Companion.CELL_FACTORY
import hextant.core.view.ListEditorControl.Companion.EMPTY_DISPLAY
import hextant.core.view.ListEditorControl.Companion.ORIENTATION
import hextant.plugins.PluginBuilder
import hextant.plugins.registerControlFactory
import ponticello.sc.editor.*

val MULTILINE = publicProperty("MULTILINE_ARGUMENTS", false)

val HIDE_NEW_KEYWORD = publicProperty("HIDE_NEW_KEYWORD", false)

val DISPLAY_BRACES = publicProperty("DISPLAY_BRACES", true)

internal fun PluginBuilder.registerControlFactories() {
    registerControlFactory { editor: AccessKeyEditor, arguments ->
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
    }

    registerControlFactory { editor: AssignmentEditor, arguments ->
        CompoundEditorControl(editor, arguments) {
            horizontal {
                styleClass("compound-expr", "assignment")
                view(editor.assignee)
                operator(" = ")
                view(editor.expression)
                root.centerChildren()
            }
        }
    }

    registerControlFactory { editor: OperatorExprEditor, arguments ->
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
    }

    registerControlFactory { editor: PropertyAccessExprEditor, arguments ->
        CompoundEditorControl(editor, arguments) {
            horizontal {
                view(editor.receiver)
                operator(".")
                view(editor.property)
                root.centerChildren()
            }
        }
    }

    registerControlFactory { editor: SpreadArrayEditor, arguments ->
        CompoundEditorControl(editor, arguments) {
            horizontal {
                styleClass("spread-array")
                operator("*")
                view(editor.array)
            }
        }
    }

    registerControlFactory { editor: NamedExprEditor, arguments ->
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
    }

    registerControlFactory { editor: IfExprEditor, arguments ->
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
    }

    registerControlFactory { editor: WhileExprEditor, arguments ->
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
    }

    registerControlFactory { editor: LoopExprEditor, arguments ->
        CompoundEditorControl(editor, arguments) {
            vertical {
                styleClass("compound-expr", "loop-expr")
                keyword("loop")
                indented {
                    view(editor.block)
                }
            }
        }
    }

    registerControlFactory { editor: FunctionDefEditor, args ->
        CompoundEditorControl(editor, args) {
            vertical {
                horizontal {
                    keyword("func ")
                    view(editor.name)
                    operator("(")
                    view(editor.parameters) {
                        set(ORIENTATION, ListEditorControl.Orientation.Horizontal)
                        set(CELL_FACTORY) { ListEditorControl.SeparatorCell(",") }
                        set(ADD_WITH_COMMA, true)
                    }
                    operator(")")
                }
                indented {
                    view(editor.body)
                }
            }
        }
    }

    registerControlFactory { editor: PlayObjectEditor, args ->
        CompoundEditorControl(editor, args) {
            horizontal {
                keyword("play ")
                view(editor.scoreObjectNameExpr)
                root.centerChildren()
            }
        }
    }

    registerControlFactory { editor: OperatorEditor, arguments: Bundle ->
        TokenEditorControl(editor, arguments, completer = NoCompleter, styleClass = "operator")
    }
    
    registerControlFactory { editor: ObjectControlSpecEditor, arguments ->
        CompoundEditorControl(editor, arguments) { 
            horizontal {  } //empty
        }
    }

    registerControlFactory { editor: BusControlSpecEditor, arguments ->
        CompoundEditorControl(editor, arguments) {
            horizontal {
                space()
                keyword("rate: ")
                view(editor.rate)
                space()
                keyword("channels: ")
                view(editor.channels) {
                    set(IntSpinnerControl.MIN, 1)
                    set(IntSpinnerControl.MAX, 256)
                }.maxWidth = 65.0
                space()
                root.centerChildren().styleClass("bus-control-spec")
            }
        }
    }
    registerControlFactory { editor: BufferControlSpecEditor, arguments ->
        CompoundEditorControl(editor, arguments) {
            horizontal {
                keyword("channels: ")
                view(editor.channels) {
                    set(IntSpinnerControl.MIN, 1)
                    set(IntSpinnerControl.MAX, 256)
                }.maxWidth = 65.0
                space()
                root.centerChildren().styleClass("buffer-control-spec")
            }
        }
    }

    registerControlFactory { editor: BufferPositionControlSpecEditor, arguments ->
        CompoundEditorControl(editor, arguments) {
            horizontal {
                root.centerChildren()
            }
        }
    }

    registerControlFactory { editor: NumericalControlSpecEditor, arguments ->
        CompoundEditorControl(editor, arguments) {
            horizontal {
                operator(" = ")
                view(editor.defaultValue).minWidth = 30.0
                space()
                horizontal {
                    operator("(")
                    view(editor.min)//.minWidth = 30.0
                    operator("..")
                    view(editor.max)//.minWidth = 30.0
                    operator(")")
                    root.minWidth = 100.0
                }
                space()
                keyword("step: ")
                view(editor.step).minWidth = 30.0
                space()
                keyword("lag: ")
                view(editor.lag)
                space()
                keyword("warp: ")
                view(editor.warp)
                space()
                view(editor.associatedColor).minWidth = 30.0
                styleClass("numerical-control-spec")
                root.centerChildren()
            }
        }
    }

    registerControlFactory { editor: ControlSpecEditor, arguments ->
        ChoiceEditorControl(editor, arguments).apply {
            styleClass("control-spec")
            isDisable = true // don't allow changing parameter types
        }
    }

    registerControlFactory { editor: DecimalLiteralEditor, arguments ->
        TokenEditorControl(editor, arguments, styleClass = "number")
    }
    registerControlFactory { editor: OptionalExprEditor, arguments ->
        arguments[OptionalEditorControl.EMPTY_DISPLAY] = { keyword("nil") }
        OptionalEditorControl(editor, arguments)
    }

    registerControlFactory { editor: SymbolLiteralEditor, arguments ->
        CompoundEditorControl(editor, arguments) {
            horizontal {
                operator("'")
                TokenEditorControl(editor, createBundle(), styleClass = "symbol")
                operator("'")
            }
        }
    }
    registerControlFactory { editor: StringLiteralEditor, arguments ->
        CompoundEditorControl(editor, arguments) {
            horizontal {
                operator("\"")
                TokenEditorControl(editor, createBundle(), styleClass = "string")
                operator("\"")
            }
        }
    }

    registerControlFactory { editor: BusSelector, arguments ->
        ObjectSelectorControl(editor, arguments)
    }

    registerControlFactory { editor: EventDictionaryEditor, arguments ->
        CompoundEditorControl(editor, arguments) {
            vertical {
                view(editor.entries) {
                    set(ORIENTATION, ListEditorControl.Orientation.Vertical)
                    set(EMPTY_DISPLAY) { null }
                }
                add(button("Add entry") { editor.entries.addLast() })
            }
        }
    }

    registerControlFactory { editor: InExprEditor, arguments ->
        CompoundEditorControl(editor, arguments) {
            horizontal {
                styleClass("bus-operation", "compound-expr")
                keyword("in")
                space()
                view(editor.busSelector)
            }
        }
    }

    registerControlFactory { editor: OutExprEditor, arguments ->
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
    }

    registerControlFactory { editor: ParameterReferenceEditor, arguments ->
        CompoundEditorControl(editor, arguments) {
            horizontal {
                keyword("get ")
                view(editor.parameter)
                root.centerChildren()
            }
        }
    }
}