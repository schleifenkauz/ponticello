package ponticello.ui.registry

import hextant.core.view.CompoundEditorControl
import hextant.core.view.EditorControl
import javafx.scene.Node
import ponticello.model.instr.ProcessDefObject
import ponticello.ui.misc.CodePane

class ProcessDefObjectPane(
    def: ProcessDefObject, enableActions: Boolean,
) : ParameterizedObjectDefPane<ProcessDefObject>(def) {
    private lateinit var setupCodePane: EditorControl<*>

    override fun getContent(def: ProcessDefObject): Node {
        val rootControl = CompoundEditorControl.vertical {
            horizontal { keyword("arg"); space(); text("inst"); operator(", "); text("duration"); operator(";") }
            add(def.body.control)
            styleClass("code-pane")
        }
        return CodePane(null, rootControl, def.context)
    }

    override fun requestFocus() {
        setupCodePane.receiveFocus()
    }
}