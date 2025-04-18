package xenakis.sc.editor

import xenakis.model.obj.SynthDefObject
import xenakis.model.registry.ObjectRegistry
import xenakis.model.registry.SynthDefRegistry
import xenakis.ui.launcher.XenakisMainActivity

class SynthDefSelector : ObjectSelector<SynthDefObject>() {
    override fun getRegistry(): ObjectRegistry<SynthDefObject> = context[SynthDefRegistry]

    override fun createNewObject(name: String): SynthDefObject? =
        context[XenakisMainActivity].synthDefsPane.createNewObject()
}