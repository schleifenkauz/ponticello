package ponticello.ui.registry

import hextant.core.view.CompoundEditorControl
import hextant.core.view.EditorControl
import javafx.scene.Node
import ponticello.model.obj.ProcessDefObject
import ponticello.ui.misc.CodePane

class ProcessDefObjectPane(
    def: ProcessDefObject, enableActions: Boolean,
) : ParameterizedObjectDefPane<ProcessDefObject>(def) {
    private lateinit var setupCodePane: EditorControl<*>

    override fun getContent(def: ProcessDefObject): Node {
        val rootControl = CompoundEditorControl.vertical {
            horizontal { keyword("arg"); space(); text("t"); operator(", "); text("duration"); operator(";") }
            setupCodePane = def.setupBlock.control
            add(setupCodePane)
            horizontal { keyword("while"); space(); text("t"); operator(" <= "); text("duration") }
            indented {
                add(def.loopBlock.control)
                horizontal { keyword("wait "); add(def.deltaExpr.control) }
            }
            styleClass("code-pane")
        }
        return CodePane(null, rootControl, def.context)
    }

    override fun requestFocus() {
        setupCodePane.receiveFocus()
    }
}