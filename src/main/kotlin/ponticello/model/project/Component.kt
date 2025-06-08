package ponticello.model.project

import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer

data class Component<T>(val name: String, val default: () -> T, val serializer: ComponentSerializer<T>) {
    var onSave: (T) -> Unit = {}
        private set

    init {
        serializer.initialize(this)
    }

    fun onSave(handler: (T) -> Unit): Component<T> {
        onSave = handler
        return this
    }

    companion object {
        inline operator fun <reified T> invoke(
            name: String, noinline default: () -> T, serializer: KSerializer<T> = serializer<T>(),
        ) = Component(name, default, SingleFileComponentSerializer(serializer))
    }
}