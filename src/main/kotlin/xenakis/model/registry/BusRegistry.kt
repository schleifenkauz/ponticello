package xenakis.model.registry

import bundles.PublicProperty
import bundles.publicProperty
import bundles.set
import hextant.context.Context
import kotlinx.serialization.Serializable
import reaktive.value.now
import xenakis.model.obj.BusObject
import xenakis.model.obj.SuperColliderObject
import xenakis.sc.Rate

@Serializable
class BusRegistry(private val busses: MutableList<BusObject>) : SuperColliderObjectRegistry<BusObject>() {
    override val objects: MutableList<BusObject>
        get() = busses

    override val objectType: String
        get() = "Bus"

    override val liveCycleType: SuperColliderObject.LiveCycleType
        get() = SuperColliderObject.LiveCycleType.ServerBoot

    override fun initialize(context: Context) {
        super.initialize(context)
        context[BusRegistry] = this
    }

    override fun getDefault(): BusObject = getOutput()

    fun getOutput() = busses.find { b -> b.type == BusObject.Type.Output }
        ?: error("No output bus found in registry")

    fun getInput() = busses.find { b -> b.type == BusObject.Type.Input }
        ?: error("No output bus found in registry")

    fun filter(rate: Rate, channels: Int): List<BusObject> = busses.filter { b ->
        b.rate == rate && b.channels.now == channels
    }

    companion object : PublicProperty<BusRegistry> by publicProperty("BusRegistry") {
        fun createDefault(): BusRegistry = BusRegistry(mutableListOf(BusObject.output, BusObject.input))
    }
}