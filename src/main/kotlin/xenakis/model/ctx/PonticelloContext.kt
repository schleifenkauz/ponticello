package xenakis.model.ctx

import bundles.PublicProperty
import bundles.publicProperty
import xenakis.model.obj.ParameterizedObject

class PonticelloContext(
    val associatedObject: ParameterizedObject? = null,
    val rootScope: Scope
) {
    companion object : PublicProperty<PonticelloContext> by publicProperty("PonticelloContext")
}