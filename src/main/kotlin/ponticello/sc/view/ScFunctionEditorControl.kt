package ponticello.sc.view

import bundles.Bundle
import bundles.createBundle
import bundles.publicProperty
import hextant.core.view.CompoundEditorControl
import javafx.scene.Node
import ponticello.sc.editor.IdentifierListEditor
import reaktive.collection.binding.isEmpty
import reaktive.collection.binding.isNotEmpty
import reaktive.collection.binding.size
import reaktive.value.binding.and
import reaktive.value.binding.equalTo
import reaktive.value.fx.asObservableValue
import reaktive.value.now

class ScFunctionEditorControl (
    private val editor: ponticello.sc.editor.ScFunctionEditor,
    arguments: Bundle
) : CompoundEditorControl(editor, arguments) {
    constructor(editor: ponticello.sc.editor.ScFunctionEditor): this(editor, createBundle())

    val canBeSingleLine =
        editor.body.statements.editors.size.equalTo(1) and editor.body.variables.editors.isEmpty()

    init {
        triggerLayoutOnChange(canBeSingleLine)
        supportArguments(SINGLE_LINE_FUNCTION)
    }

    override fun build(): Layout = if (arguments[SINGLE_LINE_FUNCTION] && canBeSingleLine.now) horizontal {
        styleClass("compound-expr", "function", "function-singleline")
        viewParameters(editor.parameters)
        operator(" -> ")
        view(editor.body.statements.editors.now[0])
    } else vertical {
        styleClass("compound-expr", "function", "function-multiline")
        horizontal {
            val argKW = keyword("arg")
            val space = space()
            viewParameters(editor.parameters, argKW, space)
        }
        CodeBlockEditorControl.displayVarsAndStatements(this@vertical, editor.body)
    }

    private fun Layout.viewParameters(parameters: IdentifierListEditor, vararg extraNodes: Node) {
        val view = viewHorizontal(parameters)
        val notEmpty = parameters.editors.isNotEmpty().asObservableValue()
        for (node in listOf(view) + extraNodes) {
            node.visibleProperty().bind(notEmpty)
            node.managedProperty().bind(notEmpty)
        }
    }

    companion object {
        val SINGLE_LINE_FUNCTION = publicProperty("SINGLE_LINE_FUNCTION", false)
    }
}

