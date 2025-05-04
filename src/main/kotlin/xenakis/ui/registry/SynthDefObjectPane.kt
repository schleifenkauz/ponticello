package xenakis.ui.registry

import hextant.core.view.EditorControl
import javafx.scene.Node
import xenakis.model.obj.CustomizableSynthDefObject
import xenakis.ui.misc.CodePane

class SynthDefObjectPane(
    def: CustomizableSynthDefObject, enableActions: Boolean,
) : ParameterizedObjectDefPane<CustomizableSynthDefObject>(def, enableActions) {
    private lateinit var ugenGraphPane: EditorControl<*>

    override fun getContent(def: CustomizableSynthDefObject): Node {
        ugenGraphPane = def.ugenGraph!!.control
        return CodePane(def.ugenGraph)
    }

    override fun requestFocus() {
        ugenGraphPane.receiveFocus()
    }
}