package ponticello.model.registry

import bundles.PublicProperty
import bundles.publicProperty
import bundles.set
import hextant.context.Context
import kotlinx.serialization.Serializable
import ponticello.model.player.ClockObject

@Serializable
class ClockRegistry(override val objects: MutableList<ClockObject>) : ObjectRegistry<ClockObject>() {
    override val objectType: String
        get() = "Clock"

    override fun initialize(context: Context) {
        super.initialize(context)
        context[ClockRegistry] = this
    }

    override fun syncAll() {}

    override fun getDefault(): ClockObject = get("default")

    companion object: PublicProperty<ClockRegistry> by publicProperty("ClockRegistry") {
        fun createDefault() = ClockRegistry(mutableListOf(ClockObject.withName("default")))
    }
}