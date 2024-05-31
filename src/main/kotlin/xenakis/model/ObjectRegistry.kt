package xenakis.model

import hextant.context.Context
import hextant.core.editor.ListenerManager
import kotlinx.serialization.Transient
import reaktive.value.now

abstract class ObjectRegistry<O : NamedObject> {
    protected abstract val objects: MutableList<O>

    abstract val objectType: String

    @Transient
    val views: ListenerManager<out View<O>> = ListenerManager.createWeakListenerManager()

    @Transient
    lateinit var context: Context
        private set

    open fun initialize(context: Context) {
        this.context = context
        for (obj in objects) {
            obj.initialize(context)
        }
    }

    open fun get(name: String): O = objects.find { it.name.now == name } ?: error("Object $name not found in $this")

    fun all(): List<O> = objects

    fun has(name: String) = objects.any { it.name.now == name }

    fun add(obj: O, idx: Int = objects.size) {
        obj.initialize(context)
        objects.add(idx, obj)
        onAdded(obj, idx)
        views.notifyListeners { added(obj, idx) }
    }

    open fun remove(obj: O) {
        val idx = objects.indexOf(obj)
        if (idx == -1) error("Object ${obj.name.now} not found in $this")
        objects.removeAt(idx)
        onRemoved(obj, idx)
        views.notifyListeners { removed(obj, idx) }
    }

    protected open fun onAdded(obj: O, idx: Int) {}

    protected open fun onRemoved(obj: O, idx: Int) {}

    fun addView(view: View<O>) {
        @Suppress("UNCHECKED_CAST") val unsafe = views as ListenerManager<View<O>>
        unsafe.addListener(view)
        for ((idx, bus) in objects.withIndex()) {
            view.added(bus, idx)
        }
    }

    interface View<in O : NamedObject> {
        fun added(obj: O, idx: Int)
        fun removed(obj: O, idx: Int)
    }
}