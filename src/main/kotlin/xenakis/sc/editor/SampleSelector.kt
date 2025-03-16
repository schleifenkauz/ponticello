package xenakis.sc.editor

import xenakis.model.obj.SampleObject
import xenakis.model.registry.ObjectReference
import xenakis.model.registry.ObjectRegistry
import xenakis.model.registry.SampleRegistry
import xenakis.ui.registry.SampleRegistryPane
import kotlin.reflect.KClass

class SampleSelector : ObjectSelector<SampleObject>() {
    override fun getRegistry(): ObjectRegistry<SampleObject> = context[SampleRegistry]

    override fun createNewObject(name: String): SampleObject? = context[SampleRegistryPane].addObject(name)
}