package xenakis.model

import hextant.context.Context
import hextant.core.editor.ListenerManager
import hextant.undo.AbstractEdit
import hextant.undo.UndoManager
import kotlinx.serialization.Transient
import reaktive.value.now
import java.util.logging.Logger

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

    abstract fun getDefault(): O

    open fun get(name: String): O = objects.find { it.name.now == name }
        ?: throw NoSuchElementException("Object $name not found in $this")

    fun all(): List<O> = objects

    fun has(name: String) = objects.any { it.name.now == name }
    fun has(obj: O) = obj in objects

    open fun add(obj: O, idx: Int = objects.size) {
        if (has(obj.name.now)) error("$objectType with name ${obj.name.now} already registered.")
        objects.add(idx, obj)
        onAdded(obj, idx)
        context[UndoManager].record(Edit.AddObject(this, obj, idx))
        views.notifyListeners { added(obj, idx) }
    }

    open fun remove(obj: O) {
        val idx = objects.indexOf(obj)
        if (idx == -1) error("Object ${obj.name.now} not found in $this")
        objects.removeAt(idx)
        context[UndoManager].record(Edit.RemoveObject(this, obj, idx))
        onRemoved(obj, idx)
        views.notifyListeners { removed(obj, idx) }
    }

    protected open fun onAdded(obj: O, idx: Int) {
        obj.initialize(context)
    }

    protected open fun onRemoved(obj: O, idx: Int) {
        obj.remove()
    }

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

    fun addView(view: View<O>) {
        @Suppress("UNCHECKED_CAST") val unsafe = views as ListenerManager<View<O>>
        unsafe.addListener(view)
        for ((idx, bus) in objects.withIndex()) {
            view.added(bus, idx)
        }
    }

    companion object {
        private val logger = Logger.getLogger("ObjectRegistry")
    }

    interface View<in O : NamedObject> {
        fun added(obj: O, idx: Int)
        fun removed(obj: O, idx: Int)
    }
}