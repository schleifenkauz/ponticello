package ponticello.model.registry

import bundles.PublicProperty
import bundles.publicProperty
import bundles.set
import hextant.context.Context
import kotlinx.serialization.Serializable
import reaktive.value.now
import ponticello.model.obj.BusObject
import ponticello.model.obj.SuperColliderObject
import ponticello.sc.Rate

@Serializable
class BusRegistry(
    override val objects: MutableList<BusObject>,
) : SuperColliderObjectRegistry<BusObject>() {
    override val objectType: String
        get() = "Bus"

    override val liveCycleType: SuperColliderObject.LiveCycleType
        get() = SuperColliderObject.LiveCycleType.ServerBoot

    override fun initialize(context: Context) {
        super.initialize(context)
        context[BusRegistry] = this
        client.onTreeCleared {
            client.run {
                for (bus in all().filterIsInstance<BusObject.ControlBus>()) {
                    bus.run { setDefaultValue(skipIfZero = false) }
                }
            }
        }
    }

    override fun getDefault(): BusObject = getOutput()

    fun getOutput() = find { b -> b.busType == BusObject.Type.Output }
        ?: error("No output bus found in registry")

    fun getInput() = find { b -> b.busType == BusObject.Type.Input }
        ?: error("No output bus found in registry")

    fun filter(rate: Rate, channels: Int): List<BusObject> = filter { b ->
        b.rate == rate && b.channels.now == channels
    }

    companion object : PublicProperty<BusRegistry> by publicProperty("BusRegistry") {
        fun createDefault(): BusRegistry = BusRegistry(mutableListOf(BusObject.output, BusObject.input))
    }
}