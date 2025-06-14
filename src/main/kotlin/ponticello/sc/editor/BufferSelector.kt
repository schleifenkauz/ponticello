package ponticello.sc.editor

import javafx.scene.input.DataFormat
import ponticello.model.obj.BufferObject
import ponticello.model.registry.BufferRegistry
import ponticello.model.registry.ObjectRegistry
import ponticello.ui.dock.AppLayout
import ponticello.ui.registry.BufferRegistryPane
import reaktive.value.ReactiveValue
import reaktive.value.now
import reaktive.value.reactiveValue

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

    override fun createNewObject(name: String): BufferObject? =
        context[AppLayout].get<BufferRegistryPane>().createNewObject(name, null)

    override fun dataFormat(): DataFormat = BufferObject.DATA_FORMAT
}