package xenakis.ui.registry

import xenakis.model.obj.AbstractSuperColliderObject
import xenakis.model.registry.SuperColliderObjectRegistry

abstract class SuperColliderObjectRegistryPane<O : AbstractSuperColliderObject>(
    private val objectRegistry: SuperColliderObjectRegistry<O>
) : ObjectRegistryPane<O>(objectRegistry) {
    override fun sync() {
        objectRegistry.syncAll()
        objectRegistry.save()
    }
}