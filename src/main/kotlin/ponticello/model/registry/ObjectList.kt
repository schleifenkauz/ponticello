package ponticello.model.registry

import fxutils.undo.UndoManager
import hextant.context.Context
import hextant.core.editor.ListenerManager
import kotlinx.serialization.Transient
import ponticello.impl.Logger
import ponticello.model.obj.AbstractContextualObject
import ponticello.model.obj.ContextualObject
import reaktive.Observer

abstract class ObjectList<O> : List<O>, AbstractContextualObject() {
    protected abstract val objects: MutableList<O>

    abstract val objectType: String

    @Transient
    protected val listeners: ListenerManager<out Listener<O>> = ListenerManager.createWeakListenerManager()

    override fun initialize(context: Context) {
        super.initialize(context)
        for (obj in objects) {
            initializeObject(obj)
        }
    }

    override fun indexOf(element: O): Int = objects.indexOf(element)

    override fun get(index: Int): O = objects[index]

    override fun contains(element: O): Boolean = objects.contains(element)

    override fun containsAll(elements: Collection<O>): Boolean = objects.containsAll(elements)

    override fun isEmpty(): Boolean = objects.isEmpty()

    override fun iterator(): Iterator<O> = objects.iterator()

    override fun lastIndexOf(element: O): Int = objects.lastIndexOf(element)

    override fun listIterator(): ListIterator<O> = objects.listIterator()

    override fun listIterator(index: Int): ListIterator<O> = objects.listIterator(index)

    override fun subList(fromIndex: Int, toIndex: Int): List<O> = objects.subList(fromIndex, toIndex)

    override val size: Int
        get() = objects.size

    fun all(): List<O> = objects.toList()

    fun has(obj: O) = objects.contains(obj)

    protected open fun initializeObject(obj: O) {
        if (obj is ContextualObject && !obj.initialized) {
            obj.initialize(context)
        }
    }

    open fun add(obj: O, idx: Int = objects.size) {
        initializeObject(obj)
        objects.add(idx, obj)
        Logger.info("Adding $obj to ${javaClass.simpleName}", Logger.Category.Registries)
        onAdded(obj, idx)
        context[UndoManager].record(ListEdit.AddObject(this, obj, idx))
        listeners.notifyListeners { added(obj, idx) }
    }

    open fun remove(obj: O) {
        val idx = objects.indexOf(obj)
        if (idx == -1) {
            Logger.severe("Object $obj not found in $this")
            return
        }
        Logger.info("Removing $obj from ${javaClass.simpleName}", Logger.Category.Registries)
        objects.removeAt(idx)
        onRemoved(obj, idx)
        context[UndoManager].record(ListEdit.RemoveObject(this, obj, idx))
        listeners.notifyListeners { removed(obj) }
    }

    fun move(obj: O, idx: Int) {
        val oldIdx = objects.indexOf(obj)
        if (oldIdx == idx) return
        Logger.info("Moving $obj to $idx", Logger.Category.Registries)
        if (oldIdx == -1) error("Object $obj not found in $this")
        objects.removeAt(oldIdx)
        objects.add(idx, obj)
        onMoved(obj, oldIdx, idx)
        context[UndoManager].record(ListEdit.MoveObject(this@ObjectList, obj, oldIdx, idx))
        listeners.notifyListeners { moved(obj, idx) }
    }

    fun addListener(listener: Listener<O>, initialize: Boolean = true) {
        @Suppress("UNCHECKED_CAST") val unsafe = listeners as ListenerManager<Listener<O>>
        unsafe.addListener(listener)
        if (initialize) {
            for ((idx, bus) in objects.withIndex()) {
                listener.added(bus, idx)
            }
        }
    }
    fun removeListener(listener: Listener<O>) {
        @Suppress("UNCHECKED_CAST") val unsafe = listeners as ListenerManager<Listener<O>>
        unsafe.removeListener(listener)
    }

    protected inline fun <reified L : Listener<O>> notifyListeners(crossinline action: L.() -> Unit) {
        listeners.notifyListeners { if (this is L) action() }
    }

    protected open fun onAdded(obj: O, idx: Int) {}

    protected open fun onRemoved(obj: O, idx: Int) {}

    protected open fun onMoved(obj: O, oldIdx: Int, newIdx: Int) {}

    fun observeEach(observe: (O) -> Observer): Observer {
        val observers = mutableMapOf<O, Observer>()
        val listener = object : Listener<O> {
            override fun added(obj: O, idx: Int) {
                observers[obj] = observe(obj)
            }

            override fun removed(obj: O) {
                observers.remove(obj)?.kill()
            }
        }
        addListener(listener)
        return Observer {
            removeListener(listener)
            observers.values.forEach { obs -> obs.kill() }
            observers.clear()
        }
    }

    interface Listener<in O> {
        fun added(obj: O, idx: Int)
        fun removed(obj: O)
        fun moved(obj: O, idx: Int) {
        }
    }
}