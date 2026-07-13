package ponticello.sc.editor

import fxutils.prompt.PromptPlacement
import javafx.scene.input.DataFormat
import ponticello.model.instr.BusObject
import ponticello.model.obj.project
import ponticello.model.registry.chooseTargetMixer
import ponticello.model.server.BusRegistry
import ponticello.sc.Rate
import reaktive.value.ReactiveValue
import reaktive.value.now
import reaktive.value.reactiveValue

class BusSelector : ObjectSelector<BusObject>() {
    override fun getOptions(): List<BusObject> = context[BusRegistry]

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

    override val canCreateItem: Boolean get() = true

    override fun createNewObject(name: String, promptPlacement: PromptPlacement): BusObject {
        val rate = expectedRate.now ?: Rate.Audio
        val channels = expectedChannels.now ?: if (rate == Rate.Audio) 2 else 1
        return BusObject.create(rate, name, channels).chooseTargetMixer(context.project, promptPlacement)
    }

    override fun dataFormat(): DataFormat = BusObject.DATA_FORMAT
}