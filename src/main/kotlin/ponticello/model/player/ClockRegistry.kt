package ponticello.model.player

import bundles.PublicProperty
import bundles.publicProperty
import bundles.set
import hextant.context.Context
import kotlinx.serialization.Serializable
import ponticello.model.registry.ObjectRegistry

@Serializable
class ClockRegistry(override val objects: MutableList<ClockObject>) : ObjectRegistry<ClockObject>() {
    override val objectType: String
        get() = "Clock"

    override fun initialize(context: Context) {
        super.initialize(context)
        context[ClockRegistry] = this
    }

    override fun getDefault(): ClockObject = get("default")

    fun stopAll() {
        for (clock in objects) {
            clock.dispose()
        }
    }

    companion object: PublicProperty<ClockRegistry> by publicProperty("ClockRegistry") {
        fun createDefault() = ClockRegistry(mutableListOf(ClockObject.withName("default")))
    }
}