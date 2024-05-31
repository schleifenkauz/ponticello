package xenakis.model

import bundles.PublicProperty
import bundles.publicProperty
import bundles.set
import hextant.context.Context
import kotlinx.serialization.Serializable
import reaktive.value.now
import xenakis.impl.SuperColliderContext
import xenakis.sc.Rate

@Serializable
class BusRegistry(private val busses: MutableList<BusObject>) : ObjectRegistry<BusObject>() {
    override val objects: MutableList<BusObject>
        get() = busses

    override val objectType: String
        get() = "Bus"

    override fun initialize(context: Context) {
        super.initialize(context)
        context[BusRegistry] = this
    }

    override fun getDefault() = busses.find { b -> b.isOutput } ?: error("No output bus found in registry")

    override fun onRemoved(obj: BusObject, idx: Int) {
        obj.removed()
    }

    fun SuperColliderContext.reallocateBusses() = run {
        for (bus in busses) {
            bus.reallocate()
        }
    }

    fun filter(rate: Rate?, channels: Int): List<BusObject> = busses.filter { b ->
        (rate == null || b.rate.now == rate) && (channels == -1 || b.channels.now == channels)
    }

    companion object : PublicProperty<BusRegistry> by publicProperty("BusRegistry") {
        fun createDefault(): BusRegistry = BusRegistry(mutableListOf(BusObject.output))
    }
}