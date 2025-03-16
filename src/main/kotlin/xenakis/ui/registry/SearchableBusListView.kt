package xenakis.ui.registry

import reaktive.value.now
import xenakis.model.obj.BusObject
import xenakis.model.registry.BusRegistry
import xenakis.sc.Rate

class SearchableBusListView(
    registry: BusRegistry, title: String,
    private val rate: Rate? = null, private val channels: Int? = null
) : SearchableRegistryView<BusObject>(registry, title) {
    override fun options(): List<BusObject> = super.options()
        .filter { bus ->
            (rate == null || bus.rate == rate) && (channels == null || bus.channels.now == channels)
        }

    override fun displayText(option: BusObject): String =
        "${option.name.now}: ${option.channels.now} x ${option.rate}"

    override fun extractText(option: BusObject): String = option.name.now

    override fun createObject(name: String): BusObject = BusObject.audio(name)
}