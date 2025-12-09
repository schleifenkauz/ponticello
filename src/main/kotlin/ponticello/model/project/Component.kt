package ponticello.model.project

import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer

data class Component<T>(
    val name: String, val displayName: String,
    val default: () -> T, val serializer: ComponentSerializer<T>
) {
    var onSave: (T) -> Unit = {}
        private set

    val gitFilePattern: String?
        get() = when (serializer) {
            is SingleFileComponentSerializer<*> -> "data/$name.json"
            is MultiFileComponentSerializer<*, *> -> "data/$name"
            is AudioFlowsSerializer -> "data/flows"
            else -> null
        }

    init {
        serializer.initialize(this)
    }

    fun onSave(handler: (T) -> Unit): Component<T> {
        onSave = handler
        return this
    }

    companion object {
        inline operator fun <reified T> invoke(
            name: String, displayName: String = name,
            noinline default: () -> T, serializer: KSerializer<T> = serializer<T>(),
        ): Component<T> = Component(name, displayName, default, SingleFileComponentSerializer(serializer))
    }
}