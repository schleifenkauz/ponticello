package xenakis.model.registry

import bundles.PublicProperty
import bundles.publicProperty
import bundles.set
import hextant.context.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import reaktive.Observer
import xenakis.model.obj.ProcessDefObject
import xenakis.model.obj.SuperColliderObject
import xenakis.sc.client.SuperColliderClient

@Serializable
class ProcessDefRegistry(
    override val objects: MutableList<ProcessDefObject> = mutableListOf(),
) : SuperColliderObjectRegistry<ProcessDefObject>() {
    @Transient
    private lateinit var updateObserver: Observer

    override val objectType: String
        get() = "ProcessDef"

    override val liveCycleType: SuperColliderObject.LiveCycleType
        get() = SuperColliderObject.LiveCycleType.InterpreterBoot

    override fun initialize(context: Context) {
        super.initialize(context)
        context[ProcessDefRegistry] = this
        updateObserver = context[SuperColliderClient].updatedProcessDef.observe { _, name ->
            val def = getOrNull(name) ?: return@observe
            def.update.fire()
        }
    }

    companion object : PublicProperty<ProcessDefRegistry> by publicProperty("ProcessDefRegistry") {
        fun createDefault() = ProcessDefRegistry()
    }
}