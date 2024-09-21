package xenakis.model

import bundles.PublicProperty
import bundles.publicProperty
import bundles.set
import hextant.context.Context
import kotlinx.serialization.Serializable
import reaktive.value.now
import xenakis.sc.Rate

@Serializable
class BusRegistry(private val busses: MutableList<BusObject>) : SuperColliderObjectRegistry<BusObject>() {
    override val objects: MutableList<BusObject>
        get() = busses

    override val objectType: String
        get() = "Bus"

    override fun initialize(context: Context) {
        super.initialize(context)
        context[BusRegistry] = this
    }

    override fun getDefault() = busses.find { b -> b.type == BusObject.Type.Output }
        ?: error("No output bus found in registry")

    fun filter(rate: Rate?, channels: Int): List<BusObject> = busses.filter { b ->
        (rate == null || b.rate.now == rate) && (channels == -1 || b.channels.now == channels)
    }

    companion object : PublicProperty<BusRegistry> by publicProperty("BusRegistry") {
        fun createDefault(): BusRegistry = BusRegistry(mutableListOf(BusObject.output, BusObject.input))
    }
}