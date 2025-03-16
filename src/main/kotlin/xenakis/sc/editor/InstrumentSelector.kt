package xenakis.sc.editor

import xenakis.model.obj.InstrumentObject
import xenakis.model.registry.InstrumentRegistry
import xenakis.model.registry.ObjectRegistry
import xenakis.ui.launcher.XenakisMainActivity

class InstrumentSelector : ObjectSelector<InstrumentObject>() {
    override fun getRegistry(): ObjectRegistry<InstrumentObject> = context[InstrumentRegistry]

    override fun createNewObject(name: String): InstrumentObject? =
        context[XenakisMainActivity].instrumentsPane.createSynthDef(name)
}