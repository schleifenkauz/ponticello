package xenakis.model.registry

import bundles.PublicProperty
import bundles.publicProperty
import bundles.set
import hextant.context.Context
import kotlinx.serialization.Serializable
import xenakis.model.obj.ProcessDefObject
import xenakis.model.obj.SuperColliderObject

@Serializable
class ProcessDefRegistry(
    override val objects: MutableList<ProcessDefObject> = mutableListOf(),
) : SuperColliderObjectRegistry<ProcessDefObject>() {
    override val objectType: String
        get() = "ProcessDef"

    override val liveCycleType: SuperColliderObject.LiveCycleType
        get() = SuperColliderObject.LiveCycleType.InterpreterBoot

    override fun initialize(context: Context) {
        super.initialize(context)
        context[ProcessDefRegistry] = this
    }

    companion object : PublicProperty<ProcessDefRegistry> by publicProperty("ProcessDefRegistry") {
        fun createDefault() = ProcessDefRegistry()
    }
}