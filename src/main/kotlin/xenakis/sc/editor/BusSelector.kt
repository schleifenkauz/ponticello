package xenakis.sc.editor

import hextant.context.Context
import hextant.serial.Snapshot
import hextant.serial.SnapshotAware
import kotlinx.serialization.Serializable
import reaktive.value.ReactiveBoolean
import reaktive.value.ReactiveString
import reaktive.value.binding.binding
import xenakis.model.*
import xenakis.sc.Rate

@Suppress("SERIALIZER_TYPE_INCOMPATIBLE")
@Serializable(with = SnapshotAware.Serializer::class)
class BusSelector(
    context: Context,
    val preferredRate: Rate? = null,
    val preferredChannels: Int = -1,
    initialValue: BusObjectReference = getDefaultBus(context, preferredRate, preferredChannels)
) : ObjectSelector<BusObject, BusObjectReference>(context, initialValue) {
    override val registry: ObjectRegistry<BusObject>
        get() = context[BusRegistry]

    override fun createNewObject(name: String): BusObject {
        val channels = preferredChannels.takeIf { it != -1 } ?: 2
        val rate = preferredRate ?: Rate.Audio
        return BusObject.create(name, rate, channels)
    }

    override fun canSelect(choice: BusObject): ReactiveBoolean =
        binding(choice.rate, choice.channels) { rate, channels ->
            (preferredRate == null || rate == preferredRate)
                    && (preferredChannels == -1 || channels == preferredChannels)
        }

    override fun extractText(choice: BusObject): ReactiveString =
        binding(choice.name, choice.rate, choice.channels) { name, rate, channels -> "$name ($rate x $channels)" }

    override fun createSnapshot(): Snapshot<*> = Snap()

    private class Snap : ObjectSelector.Snap<BusObject, BusObjectReference>() {
        override val serializer: ObjectReference.Serializer<BusObjectReference>
            get() = BusObjectReference.Serializer
    }

    companion object {
        private fun getDefaultBus(
            context: Context,
            preferredRate: Rate?,
            preferredChannels: Int
        ): BusObjectReference {
            val registry = context[BusRegistry]
            val adequate = registry.filter(preferredRate, preferredChannels).firstOrNull()
            val bus = adequate ?: registry.getOutputBus()
            return bus.createReference()
        }
    }
}