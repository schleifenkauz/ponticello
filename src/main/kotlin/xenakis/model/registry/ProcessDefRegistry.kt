package xenakis.model.registry

import bundles.PublicProperty
import bundles.publicProperty
import bundles.set
import hextant.context.Context
import kotlinx.serialization.Serializable
import xenakis.model.obj.ProcessDefObject

@Serializable
class ProcessDefRegistry(
    override val objects: MutableList<ProcessDefObject> = mutableListOf(),
    private var selected: ObjectReference? = null
) : SuperColliderObjectRegistry<ProcessDefObject>() {
    override val objectType: String
        get() = "ProcessDef"

    override val componentName: String
        get() = "process_defs"

    override fun initialize(context: Context) {
        super.initialize(context)
        selected?.resolve(this)
        context[ProcessDefRegistry] = this
    }

    val selectedDef: ProcessDefObject? get() = selected?.get()

    fun select(def: ProcessDefObject) {
        selected = def.reference()
    }

    override fun getDefault(name: String?): ProcessDefObject = error("No default Process object available")

    companion object : PublicProperty<ProcessDefRegistry> by publicProperty("ProcessDefRegistry")
}