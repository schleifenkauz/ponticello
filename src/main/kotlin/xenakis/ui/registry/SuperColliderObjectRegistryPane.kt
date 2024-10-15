package xenakis.ui.registry

import xenakis.model.obj.AbstractSuperColliderObject
import xenakis.model.registry.SuperColliderObjectRegistry

abstract class SuperColliderObjectRegistryPane<O : AbstractSuperColliderObject>(
    protected open val registry: SuperColliderObjectRegistry<O>
) : ObjectRegistryPane<O>(registry) {
    override fun sync() {
        registry.syncAll()
        registry.save()
    }
}