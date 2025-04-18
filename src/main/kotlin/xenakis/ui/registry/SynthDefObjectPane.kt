package xenakis.ui.registry

import javafx.scene.Node
import xenakis.model.obj.CustomizableSynthDefObject
import xenakis.ui.misc.CodePane

class SynthDefObjectPane(
    def: CustomizableSynthDefObject
) : ParameterizedObjectDefPane<CustomizableSynthDefObject>(def) {
    override fun getContent(def: CustomizableSynthDefObject): Node = CodePane(def.ugenGraph!!)
}