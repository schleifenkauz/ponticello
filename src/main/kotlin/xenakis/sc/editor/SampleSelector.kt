package xenakis.sc.editor

import reaktive.value.ReactiveValue
import reaktive.value.now
import reaktive.value.reactiveValue
import xenakis.model.obj.SampleObject
import xenakis.model.registry.ObjectRegistry
import xenakis.model.registry.SampleRegistry
import xenakis.ui.registry.SampleRegistryPane

class SampleSelector : ObjectSelector<SampleObject>() {
    override fun getRegistry(): ObjectRegistry<SampleObject> = context[SampleRegistry]

    private var expectedChannels: ReactiveValue<Int?> = reactiveValue(null)

    fun setFilter(channels: ReactiveValue<Int?>) {
        expectedChannels = channels
    }

    fun setFilter(channels: Int) {
        setFilter((reactiveValue(channels)))
    }

    override fun filter(obj: SampleObject): Boolean =
        expectedChannels.now == null || obj.channels == expectedChannels.now

    override fun createNewObject(name: String): SampleObject? = context[SampleRegistryPane].createNewObject(name)
}