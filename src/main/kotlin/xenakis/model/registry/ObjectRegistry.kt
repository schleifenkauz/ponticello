package xenakis.model.registry

import hextant.context.Context
import hextant.core.editor.ListenerManager
import hextant.undo.AbstractEdit
import hextant.undo.UndoManager
import kotlinx.serialization.Transient
import reaktive.value.now
import xenakis.impl.Logger
import xenakis.model.obj.AbstractContextualObject
import xenakis.ui.launcher.XenakisLauncher.Companion.currentProject

abstract class ObjectRegistry<O : NamedObject>: AbstractContextualObject() {
    protected abstract val objects: MutableList<O>

    abstract val objectType: String

    @Transient
    val views: ListenerManager<out Listener<O>> = ListenerManager.createWeakListenerManager()

    override fun initialize(context: Context) {
        super.initialize(context)
        for (obj in objects) {
            obj.initialize(context)
            obj.onLoadedIntoRegistry()
        }
    }

    fun save() {
        context[currentProject].save(this)
    }

    open fun getDefault(): O? = null

    fun get(name: String): O = getOrNull(name) ?: throw NoSuchElementException("Object $name not found in $this")

    open fun getOrNull(name: String) = objects.find { it.name.now == name }

    fun all(): List<O> = objects

    fun indexOf(obj: O): Int = objects.indexOf(obj)

    fun has(name: String) = objects.any { it.name.now == name }

    fun has(obj: O) = obj in objects

    fun overwrite(obj: O) {
        val old = get(obj.name.now)
        val index = objects.indexOf(old)
        remove(old)
        add(obj, index)
    }

    open fun add(obj: O, idx: Int = objects.size) {
        if (has(obj.name.now)) {
            Logger.severe("$objectType with name ${obj.name.now} already registered.", Logger.Category.Registries)
            return
        }
        obj.initialize(context)
        objects.add(idx, obj)
        Logger.info("Adding $obj to ${javaClass.simpleName}", Logger.Category.Registries)
        obj.onAdded(context)
        onAdded(obj, idx)
        context[UndoManager].record(Edit.AddObject(this, obj, idx))
        views.notifyListeners { added(obj, idx) }
    }

    open fun remove(obj: O) {
        val idx = objects.indexOf(obj)
        if (idx == -1) {
            Logger.severe("Object ${obj.name.now} not found in $this")
            return
        }
        Logger.info("Removing $obj from ${javaClass.simpleName}", Logger.Category.Registries)
        objects.removeAt(idx)
        obj.onRemoved()
        onRemoved(obj, idx)
        context[UndoManager].record(Edit.RemoveObject(this, obj, idx))
        views.notifyListeners { removed(obj, idx) }
    }

    protected open fun onAdded(obj: O, idx: Int) {}

    protected open fun onRemoved(obj: O, idx: Int) {}

    private sealed class Edit<O : NamedObject>(protected val registry: ObjectRegistry<O>) : AbstractEdit() {
        class AddObject<O : NamedObject>(
            registry: ObjectRegistry<O>,
            private val obj: O,
            private val idx: Int
        ) : Edit<O>(registry) {
            override val actionDescription: String
                get() = "Add ${registry.objectType}"

            override fun doUndo() {
                registry.remove(obj)
            }

            override fun doRedo() {
                registry.add(obj, idx)
            }
        }

        class RemoveObject<O : NamedObject>(
            registry: ObjectRegistry<O>,
            private val obj: O,
            private val idx: Int
        ) : Edit<O>(registry) {
            override val actionDescription: String
                get() = "Remove ${registry.objectType}"

            override fun doUndo() {
                registry.add(obj, idx)
            }

            override fun doRedo() {
                registry.remove(obj)
            }
        }
    }

    fun addListener(listener: Listener<O>, initialize: Boolean = true) {
        @Suppress("UNCHECKED_CAST") val unsafe = views as ListenerManager<Listener<O>>
        unsafe.addListener(listener)
        if (initialize) {
            for ((idx, bus) in objects.withIndex()) {
                listener.added(bus, idx)
            }
        }
    }

    fun removeListener(listener: Listener<O>) {
        @Suppress("UNCHECKED_CAST") val unsafe = views as ListenerManager<Listener<O>>
        unsafe.removeListener(listener)
    }

    interface Listener<in O : NamedObject> {
        fun added(obj: O, idx: Int)
        fun removed(obj: O, idx: Int)
    }
}