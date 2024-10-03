package xenakis.ui

import reaktive.value.now
import xenakis.model.BusObject
import xenakis.model.BusRegistry
import xenakis.sc.Rate

class SearchableBusListView(
    registry: BusRegistry, title: String,
    private val rate: Rate? = null, private val channels: Int? = null
) : SearchableRegistryView<BusObject>(registry, title) {
    override fun options(): List<BusObject> = super.options()
        .filter { bus ->
            (rate == null || bus.rate.now == rate) && (channels == null || bus.channels.now == channels)
        }

    override fun displayText(option: BusObject): String =
        "${option.name.now}: ${option.channels.now} x ${option.rate.now}"

    override fun extractText(option: BusObject): String = option.name.now

    override fun createObject(name: String): BusObject = BusObject.create(name)
}