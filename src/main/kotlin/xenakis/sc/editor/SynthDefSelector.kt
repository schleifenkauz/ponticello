package xenakis.sc.editor

import xenakis.model.obj.SynthDefObject
import xenakis.model.registry.InstrumentRegistry
import xenakis.model.registry.ObjectRegistry
import xenakis.ui.launcher.XenakisMainActivity

class SynthDefSelector : ObjectSelector<SynthDefObject>() {
    override fun getRegistry(): ObjectRegistry<SynthDefObject> =
        context[InstrumentRegistry] as ObjectRegistry<SynthDefObject> //TODO

    override fun createNewObject(name: String): SynthDefObject? =
        context[XenakisMainActivity].instrumentsPane.createSynthDef(name)
}