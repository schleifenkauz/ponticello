package xenakis.ui.registry

import fxutils.styleClass
import javafx.scene.Node
import reaktive.value.binding.map
import xenakis.model.obj.CustomizableSynthDefObject

class SynthDefObjectPane(def: CustomizableSynthDefObject) : ParameterizedObjectDefPane<CustomizableSynthDefObject>(
    def, def.name.map { n -> "SynthDef $n" }
) {
    override fun getContent(def: CustomizableSynthDefObject): Node = def.ugenGraph!!.control.styleClass("code-pane")

    override fun update() {
        def.sync()
    }
}