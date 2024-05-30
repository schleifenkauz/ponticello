package xenakis.model

import bundles.PublicProperty
import bundles.publicProperty
import bundles.set
import hextant.context.Context
import hextant.core.editor.ListenerManager
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import reaktive.value.now
import xenakis.impl.SuperColliderContext
import xenakis.sc.Rate

@Serializable
class BusRegistry(private val busses: MutableList<BusObject>) {
    @Transient
    private lateinit var context: Context

    @Transient
    val views = ListenerManager.createWeakListenerManager<View>()

    fun initialize(context: Context) {
        this.context = context
        context[BusRegistry] = this
        for (bus in busses) {
            bus.initialize(context)
        }
    }

    fun allBusses(): List<BusObject> = busses

    fun getOutputBus() = busses.find { b -> b.isOutput } ?: error("No output bus found in registry")

    fun add(bus: BusObject, idx: Int = busses.size) {
        busses.add(bus)
        bus.initialize(context)
        views.notifyListeners { added(bus, idx) }
    }

    fun remove(bus: BusObject) {
        val idx = busses.indexOf(bus)
        if (idx == -1) error("Bus $bus not found in registry")
        busses.removeAt(idx)
        bus.removed()
        views.notifyListeners { removed(bus, idx) }
    }

    fun SuperColliderContext.reallocateBusses() = run {
        for (bus in busses) {
            bus.reallocate()
        }
    }

    fun getBus(busName: String): BusObject =
        busses.find { b -> b.name.now == busName } ?: error("Bus with name $busName not found")

    fun hasBus(newName: String): Boolean = busses.any { b -> b.name.now == newName }

    fun addView(view: View) {
        views.addListener(view)
        for ((idx, bus) in busses.withIndex()) {
            view.added(bus, idx)
        }
    }

    fun filter(rate: Rate?, channels: Int): List<BusObject> = busses.filter { b ->
        (rate == null || b.rate.now == rate) && (channels == -1 || b.channels.now == channels)
    }

    companion object : PublicProperty<BusRegistry> by publicProperty("BusRegistry") {
        fun createDefault(): BusRegistry = BusRegistry(mutableListOf(BusObject.output))
    }

    interface View {
        fun added(bus: BusObject, idx: Int)

        fun removed(bus: BusObject, idx: Int)
    }
}