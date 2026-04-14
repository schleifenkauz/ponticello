package ponticello.ui.registry

import ponticello.impl.Decimal
import ponticello.impl.one
import ponticello.impl.rangeTo
import ponticello.impl.zero
import ponticello.model.score.ObjectPosition
import ponticello.model.server.AllocatedBufferObject
import ponticello.model.server.BufferObject
import ponticello.model.server.BufferRegistry
import ponticello.sc.client.SuperColliderClient
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
            "Buffer duration",
            initialValue = one,
            precision = ObjectPosition.TIME_PRECISION,
            range = zero..Decimal.INF,
            client = registry.context[SuperColliderClient]
        ).showDialog(parentPrompt = this) ?: return null
        return AllocatedBufferObject.create(name, channels, duration)
    }
}