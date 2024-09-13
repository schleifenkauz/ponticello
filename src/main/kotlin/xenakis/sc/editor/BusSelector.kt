package xenakis.sc.editor

import hextant.context.Context
import hextant.serial.SnapshotAware
import kotlinx.serialization.Serializable
import reaktive.value.ReactiveBoolean
import reaktive.value.ReactiveString
import reaktive.value.ReactiveVariable
import reaktive.value.binding.binding
import reaktive.value.reactiveVariable
import xenakis.model.BusObject
import xenakis.model.BusRegistry
import xenakis.model.ObjectReference
import xenakis.model.ObjectRegistry
import xenakis.sc.Rate
import kotlin.reflect.KClass

@Suppress("SERIALIZER_TYPE_INCOMPATIBLE")
@Serializable(with = SnapshotAware.Serializer::class)
class BusSelector(
    context: Context,
    val preferredRate: Rate? = null,
    val preferredChannels: Int = -1,
    selected: ReactiveVariable<ObjectReference>
    = reactiveVariable(getDefaultBus(context, preferredRate, preferredChannels))
) : ObjectSelector<BusObject, ObjectReference>(context, selected) {
    override fun getRegistry(context: Context): ObjectRegistry<BusObject> = context[BusRegistry]

    override val objectClass: KClass<BusObject>
        get() = BusObject::class

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

    companion object {
        private fun getDefaultBus(
            context: Context,
            preferredRate: Rate?,
            preferredChannels: Int
        ): ObjectReference {
            val registry = context[BusRegistry]
            val adequate = registry.filter(preferredRate, preferredChannels).firstOrNull()
            val bus = adequate ?: registry.getDefault()
            return bus.createReference()
        }
    }
}