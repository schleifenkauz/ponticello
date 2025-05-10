package ponticello.sc.editor

import reaktive.value.ReactiveValue
import reaktive.value.now
import reaktive.value.reactiveValue
import ponticello.model.obj.BufferObject
import ponticello.model.obj.SampleObject
import ponticello.model.registry.BufferRegistry
import ponticello.model.registry.ObjectRegistry
import ponticello.ui.registry.SampleRegistryPane

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