package ponticello.ui.registry

import ponticello.impl.Decimal
import ponticello.impl.one
import ponticello.impl.rangeTo
import ponticello.impl.zero
import ponticello.model.obj.AllocatedBufferObject
import ponticello.model.obj.BufferObject
import ponticello.model.registry.BufferRegistry
import ponticello.model.score.ObjectPosition
import ponticello.ui.controls.DecimalPrompt
import reaktive.value.now

class BufferSelectorPrompt(
    registry: BufferRegistry, title: String,
    private val channels: Int,
) : RegistrySelectorPrompt<BufferObject>(registry, title) {
    override val canCreateItem: Boolean get() = true

    override fun extractText(option: BufferObject): String = option.name.now

    override fun displayText(option: BufferObject): String = "${option.name.now} [${option.channels()}]"

    override fun options(): List<BufferObject> = super.options().filter { buf -> buf.channels() == channels }

    override fun createObject(name: String): BufferObject? {
        val duration = DecimalPrompt(
            "Buffer duration", precision = ObjectPosition.TIME_PRECISION,
            initialValue = one, range = zero..Decimal.INF
        ).showDialog(ownerWindow, anchor) ?: return null
        return AllocatedBufferObject.create(name, channels, duration)
    }
}