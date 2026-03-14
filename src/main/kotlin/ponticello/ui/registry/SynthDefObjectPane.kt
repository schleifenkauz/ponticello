package ponticello.ui.registry

import javafx.scene.Node
import ponticello.model.instr.CustomizableSynthDefObject
import ponticello.ui.misc.CodePane

class SynthDefObjectPane(
    def: CustomizableSynthDefObject
) : ParameterizedObjectDefPane<CustomizableSynthDefObject>(def) {
    override fun getContent(def: CustomizableSynthDefObject): Node {
        return CodePane(def.ugenGraph!!)
    }

    override fun requestFocus() {
        def.ugenGraph?.control?.requestFocus()
    }
}