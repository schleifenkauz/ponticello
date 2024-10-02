package xenakis.model

import hextant.context.Context
import hextant.core.editor.ListenerManager
import hextant.undo.AbstractEdit
import hextant.undo.UndoManager
import kotlinx.serialization.Transient
import reaktive.value.now
import xenakis.ui.XenakisController.Companion.currentProject
import xenakis.ui.plural

abstract class ObjectRegistry<O : NamedObject> : XenakisProject.ProjectComponent {
    protected abstract val objects: MutableList<O>

    abstract val objectType: String

    override val componentName: String
        get() = plural(objectType)

    @Transient
    val views: ListenerManager<out Listener<O>> = ListenerManager.createWeakListenerManager()

    @Transient
    lateinit var context: Context
        private set

    open fun initialize(context: Context) {
        this.context = context
        for (obj in objects) {
            obj.initialize(context)
        }
    }

    fun save() {
        context[currentProject].save(this)
    }

    abstract fun getDefault(): O

    fun get(name: String): O = getOrNull(name) ?: throw NoSuchElementException("Object $name not found in $this")

    open fun getOrNull(name: String) = objects.find { it.name.now == name }

    fun all(): List<O> = objects

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

    fun addListener(listener: Listener<O>) {
        @Suppress("UNCHECKED_CAST") val unsafe = views as ListenerManager<Listener<O>>
        unsafe.addListener(listener)
        for ((idx, bus) in objects.withIndex()) {
            listener.added(bus, idx)
        }
    }

    interface Listener<in O : NamedObject> {
        fun added(obj: O, idx: Int)
        fun removed(obj: O, idx: Int)
    }
}