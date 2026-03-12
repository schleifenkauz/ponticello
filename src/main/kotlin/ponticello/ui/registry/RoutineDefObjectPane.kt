package ponticello.ui.registry

import hextant.core.view.CompoundEditorControl
import javafx.scene.Node
import ponticello.model.instr.RoutineDefObject
import ponticello.ui.misc.CodePane

class RoutineDefObjectPane(
    def: RoutineDefObject, enableActions: Boolean,
) : ParameterizedObjectDefPane<RoutineDefObject>(def) {
    override fun getContent(def: RoutineDefObject): Node {
        val rootControl = CompoundEditorControl.vertical {
            horizontal { keyword("arg"); space(); text("inst"); operator(", "); text("duration"); operator(";") }
            add(def.body.control)
            styleClass("code-pane")
        }
        return CodePane(null, rootControl, def.context)
    }
}