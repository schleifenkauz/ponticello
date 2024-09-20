package xenakis.ui

import xenakis.model.AbstractSuperColliderObject
import xenakis.model.SuperColliderObjectRegistry

abstract class SuperColliderObjectRegistryPane<O : AbstractSuperColliderObject>(
    private val registry: SuperColliderObjectRegistry<O>
) : ObjectRegistryPane<O>(registry) {
    override fun sync() {
        registry.syncAll()
        registry.save()
    }
}