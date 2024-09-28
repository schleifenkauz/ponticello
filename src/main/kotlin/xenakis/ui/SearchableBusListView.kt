package xenakis.ui

import reaktive.value.now
import xenakis.model.BusObject
import xenakis.model.BusRegistry

class SearchableBusListView(
    registry: BusRegistry, title: String,
) : SearchableRegistryView<BusObject>(registry, title) {
    override fun displayText(option: BusObject): String =
        "${option.name.now}: ${option.channels.now} x ${option.rate.now}"

    override fun extractText(option: BusObject): String = option.name.now

    override fun createObject(name: String): BusObject = BusObject.create(name)
}