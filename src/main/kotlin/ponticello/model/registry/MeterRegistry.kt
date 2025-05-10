package ponticello.model.registry

import bundles.PublicProperty
import bundles.publicProperty
import bundles.set
import hextant.context.Context
import kotlinx.serialization.Serializable
import ponticello.model.obj.MeterObject

@Serializable
class MeterRegistry(override val objects: MutableList<MeterObject>) : ObjectRegistry<MeterObject>() {
    override val objectType: String
        get() = "Meter"

    override fun syncAll() {}

    override fun initialize(context: Context) {
        super.initialize(context)
        context[MeterRegistry] = this
    }

    companion object: PublicProperty<MeterRegistry> by publicProperty("METER_REGISTRY") {
        fun createDefault() = MeterRegistry(mutableListOf())
    }
}