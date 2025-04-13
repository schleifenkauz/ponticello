package xenakis.ui.registry

import hextant.core.view.CompoundEditorControl
import javafx.scene.Node
import reaktive.value.binding.map
import xenakis.model.obj.ProcessDefObject

class ProcessDefObjectPane(def: ProcessDefObject) : ParameterizedObjectDefPane<ProcessDefObject>(
    def, def.name.map { n -> "ProcessDef $n" }
) {
    override fun getContent(def: ProcessDefObject): Node = CompoundEditorControl.vertical {
        horizontal { keyword("arg"); space(); text("t"); operator(", "); text("duration"); operator(";") }
        add(def.setupBlock.control)
        horizontal { keyword("while"); space(); text("t"); operator(" <= "); text("duration")}
        indented {
            add(def.loopBlock.control)
            horizontal { keyword("wait "); add(def.deltaExpr.control) }
        }
        styleClass("code-pane")
    }

    override fun update() {
        def.sync()
    }
}