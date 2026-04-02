package ponticello.sc.editor

import fxutils.prompt.PromptPlacement
import javafx.scene.input.DataFormat
import ponticello.model.server.BufferObject
import ponticello.model.server.BufferRegistry
import ponticello.ui.dock.AppLayout
import ponticello.ui.registry.BufferRegistryPane
import reaktive.value.ReactiveValue
import reaktive.value.now
import reaktive.value.reactiveValue

class BufferSelector : ObjectSelector<BufferObject>() {
    override fun getOptions(): List<BufferObject> = context[BufferRegistry]

    private var expectedChannels: ReactiveValue<Int?> = reactiveValue(null)

    fun setFilter(channels: ReactiveValue<Int?>) {
        expectedChannels = channels
    }

    fun setFilter(channels: Int) {
        setFilter((reactiveValue(channels)))
    }

    override fun filter(obj: BufferObject): Boolean =
        expectedChannels.now == null || obj.channels() == expectedChannels.now

    override fun createNewObject(name: String, promptPlacement: PromptPlacement): BufferObject? =
        context[AppLayout].get<BufferRegistryPane>().createNewObject(name, promptPlacement)

    override fun dataFormat(): DataFormat = BufferObject.DATA_FORMAT
}