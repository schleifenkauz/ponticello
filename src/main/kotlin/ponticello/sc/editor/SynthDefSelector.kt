package ponticello.sc.editor

import javafx.scene.input.DataFormat
import ponticello.model.obj.SynthDefObject
import ponticello.model.registry.ObjectRegistry
import ponticello.model.registry.SynthDefRegistry
import ponticello.ui.launcher.PonticelloMainActivity

class SynthDefSelector : ObjectSelector<SynthDefObject>() {
    override fun getList(): ObjectRegistry<SynthDefObject> = context[SynthDefRegistry]

    override fun createNewObject(name: String): SynthDefObject? =
        context[PonticelloMainActivity].synthDefsPane().createNewObject(name, null)

    override fun dataFormat(): DataFormat? = SynthDefObject.DATA_FORMAT
}