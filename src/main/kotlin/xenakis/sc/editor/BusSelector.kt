package xenakis.sc.editor

import reaktive.value.ReactiveValue
import reaktive.value.now
import reaktive.value.reactiveValue
import xenakis.model.obj.BusObject
import xenakis.model.registry.BusRegistry
import xenakis.model.registry.ObjectRegistry
import xenakis.sc.Rate

class BusSelector : ObjectSelector<BusObject>() {
    override fun getList(): ObjectRegistry<BusObject> = context[BusRegistry]

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
        (expectedRate.now == null || obj.rate == expectedRate.now) &&
                (expectedChannels.now == null || obj.channels.now == expectedChannels.now)

    override fun createNewObject(name: String): BusObject {
        val rate = expectedRate.now ?: Rate.Audio
        val channels = expectedChannels.now ?: if (rate == Rate.Audio) 2 else 1
        return BusObject.create(rate, name, channels)
    }
}