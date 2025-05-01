package xenakis.sc.editor

import reaktive.value.ReactiveValue
import reaktive.value.now
import reaktive.value.reactiveValue
import xenakis.model.obj.BufferObject
import xenakis.model.obj.SampleObject
import xenakis.model.registry.BufferRegistry
import xenakis.model.registry.ObjectRegistry
import xenakis.ui.registry.SampleRegistryPane

class BufferSelector : ObjectSelector<BufferObject>() {
    override fun getList(): ObjectRegistry<BufferObject> = context[BufferRegistry]

    private var expectedChannels: ReactiveValue<Int?> = reactiveValue(null)

    fun setFilter(channels: ReactiveValue<Int?>) {
        expectedChannels = channels
    }

    fun setFilter(channels: Int) {
        setFilter((reactiveValue(channels)))
    }

    override fun filter(obj: BufferObject): Boolean =
        expectedChannels.now == null || obj.channels() == expectedChannels.now

    override fun createNewObject(name: String): SampleObject? = context[SampleRegistryPane].createNewObject(name, null)
}