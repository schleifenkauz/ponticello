package xenakis.ui.registry

import fxutils.styleClass
import javafx.scene.Node
import xenakis.model.obj.CustomizableSynthDefObject

class SynthDefObjectPane(def: CustomizableSynthDefObject) : ParameterizedObjectDefPane<CustomizableSynthDefObject>(
    def
) {
    override fun getContent(def: CustomizableSynthDefObject): Node = def.ugenGraph!!.control.styleClass("code-pane")
}