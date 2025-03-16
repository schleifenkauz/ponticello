package xenakis.sc.editor

import kotlinx.serialization.Serializable
import reaktive.value.ReactiveString
import reaktive.value.ReactiveValue
import reaktive.value.binding.binding
import reaktive.value.binding.flatMap
import reaktive.value.now
import reaktive.value.reactiveValue
import xenakis.model.obj.BusObject
import xenakis.model.registry.BusRegistry
import xenakis.model.registry.ObjectReference
import xenakis.model.registry.ObjectRegistry
import xenakis.sc.Rate

@Serializable
class BusSelector : ObjectSelector<BusObject>() {
    override fun getRegistry(): ObjectRegistry<BusObject> = context[BusRegistry]

    private var expectedRate: ReactiveValue<Rate?> = reactiveValue(null)
    private var expectedChannels: ReactiveValue<Int?> = reactiveValue(null)

    fun setFilter(
        rate: ReactiveValue<Rate?> = reactiveValue(null),
        channels: ReactiveValue<Int?> = reactiveValue(null)
    ) {
        expectedRate = rate
        expectedChannels = channels
    }

    fun setFilter(rate: Rate?, channels: Int?) {
        setFilter(reactiveValue(rate), reactiveValue(channels))
    }

    override fun filter(obj: BusObject): Boolean =
        obj.rate == expectedRate.now && obj.channels.now == expectedChannels.now

    override fun createNewObject(name: String): BusObject {
        val rate = expectedRate.now ?: Rate.Audio
        val channels = expectedChannels.now ?: if (rate == Rate.Audio) 2 else 1
        return BusObject.create(rate, name, channels)
    }

    override fun toString(choice: ObjectReference<BusObject>): ReactiveString = choice.isResolved.flatMap { resolved ->
        if (resolved) {
            val obj = choice.get()!!
            binding(choice.name, obj.channels) { name, channels -> "$name (${obj.rate} x $channels)" }
        } else reactiveValue("")
    }
}