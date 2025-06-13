package ponticello.model.project

import java.io.File

abstract class ComponentSerializer<T> {
    protected lateinit var component: Component<T>
        private set

    open fun initialize(component: Component<T>) {
        this.component = component
    }

    abstract fun serializeComponent(value: T, dataDirectory: File)

    abstract fun deserializeComponent(dataDirectory: File): T
}