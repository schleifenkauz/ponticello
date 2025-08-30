package ponticello.ui.registry

import ponticello.model.obj.BufferObject
import ponticello.model.registry.BufferRegistry
import reaktive.value.now

class BufferSelectorPrompt(
    registry: BufferRegistry, title: String,
    private val channels: Int,
) : RegistrySelectorPrompt<BufferObject>(registry, title) {
    override fun extractText(option: BufferObject): String = option.name.now

    override fun displayText(option: BufferObject): String = "${option.name.now} [${option.channels()}]"

    override fun options(): List<BufferObject> = super.options().filter { buf -> buf.channels() == channels }

    override fun createObject(name: String): BufferObject? = null //TODO create empty buffer
}