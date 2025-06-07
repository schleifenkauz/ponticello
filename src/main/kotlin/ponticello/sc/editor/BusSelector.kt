package ponticello.sc.editor

import javafx.scene.input.DataFormat
import ponticello.model.obj.BusObject
import ponticello.model.registry.BusRegistry
import ponticello.model.registry.ObjectRegistry
import ponticello.sc.Rate
import reaktive.value.ReactiveValue
import reaktive.value.now
import reaktive.value.reactiveValue

class BusSelector : ObjectSelector<BusObject>() {
    override fun getList(): ObjectRegistry<BusObject> = context[BusRegistry]

    private var expectedRate: ReactiveValue<Rate?> = reactiveValue(null)
    private var expectedChannels: ReactiveValue<Int?> = reactiveValue(null)

    fun setFilter(
        rate: ReactiveValue<Rate?> = reactiveValue(null),
        channels: ReactiveValue<Int?> = reactiveValue(null),
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

    override fun dataFormat(): DataFormat? = BusObject.DATA_FORMAT
}