package xenakis.ui.registry

import hextant.core.view.CompoundEditorControl
import javafx.scene.Node
import xenakis.model.obj.ProcessDefObject
import xenakis.ui.misc.CodePane

class ProcessDefObjectPane(
    def: ProcessDefObject,
) : ParameterizedObjectDefPane<ProcessDefObject>(def) {
    override fun getContent(def: ProcessDefObject): Node {
        val rootControl = CompoundEditorControl.vertical {
            horizontal { keyword("arg"); space(); text("t"); operator(", "); text("duration"); operator(";") }
            add(def.setupBlock.control)
            horizontal { keyword("while"); space(); text("t"); operator(" <= "); text("duration") }
            indented {
                add(def.loopBlock.control)
                horizontal { keyword("wait "); add(def.deltaExpr.control) }
            }
            styleClass("code-pane")
        }
        return CodePane(null, rootControl, def.context)
    }
}