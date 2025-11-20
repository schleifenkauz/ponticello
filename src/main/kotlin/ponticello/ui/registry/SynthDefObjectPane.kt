package ponticello.ui.registry

import hextant.core.view.EditorControl
import javafx.scene.Node
import ponticello.model.instr.CustomizableSynthDefObject
import ponticello.ui.misc.CodePane

class SynthDefObjectPane(
    def: CustomizableSynthDefObject
) : ParameterizedObjectDefPane<CustomizableSynthDefObject>(def) {
    private lateinit var ugenGraphPane: EditorControl<*>

    override fun getContent(def: CustomizableSynthDefObject): Node {
        ugenGraphPane = def.ugenGraph!!.control
        return CodePane(def.ugenGraph)
    }

    override fun requestFocus() {
        ugenGraphPane.receiveFocus()
    }
}