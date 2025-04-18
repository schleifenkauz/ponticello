package xenakis.model.registry

import hextant.context.Context
import hextant.core.editor.ListenerManager
import hextant.undo.UndoManager
import kotlinx.serialization.Transient
import reaktive.value.now
import xenakis.impl.Logger
import xenakis.model.obj.AbstractContextualObject

abstract class NamedObjectList<O : NamedObject> : List<O>, AbstractContextualObject() {
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

    fun all(): List<O> = objects

    fun get(name: String): O = getOrNull(name) ?: throw NoSuchElementException("Object $name not found in $this")

    open fun getOrNull(name: String) = objects.find { it.name.now == name }

    fun has(name: String) = objects.any { it.name.now == name }

    fun has(obj: O) = objects.contains(obj)

    fun overwrite(obj: O) {
        val old = get(obj.name.now)
        val index = objects.indexOf(old)
        remove(old)
        add(obj, index)
    }

    protected open fun initializeObject(obj: O) {
        obj.initialize(context)
    }

    open fun add(obj: O, idx: Int = objects.size) {
        if (obj.name.now != NamedObject.NO_NAME && has(obj.name.now)) {
            Logger.severe("$objectType with name ${obj.name.now} already registered.", Logger.Category.Registries)
            return
        }
        initializeObject(obj)
        objects.add(idx, obj)
        Logger.info("Adding $obj to ${javaClass.simpleName}", Logger.Category.Registries)
        obj.onAdded(context)
        onAdded(obj, idx)
        context[UndoManager].record(ListEdit.AddObject(this, obj, idx))
        listeners.notifyListeners { added(obj, idx) }
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
        context[UndoManager].record(ListEdit.RemoveObject(this, obj, idx))
        listeners.notifyListeners { removed(obj) }
    }

    fun removeByName(parameter: String) {
        val control = getOrNull(parameter) ?: error("Parameter $parameter not found in controls")
        remove(control)
    }

    fun move(obj: O, idx: Int) {
        val oldIdx = objects.indexOf(obj)
        if (oldIdx == idx) return
        Logger.info("Moving $obj to $idx", Logger.Category.Registries)
        if (oldIdx == -1) error("Object $obj not found in $this")
        objects.removeAt(oldIdx)
        if (oldIdx < idx) objects.add(idx, obj)
        else objects.add(idx, obj)
        onMoved(obj, oldIdx, idx)
        context[UndoManager].record(ListEdit.MoveObject(this@NamedObjectList, obj, oldIdx, idx))
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


    interface Listener<in O : NamedObject> {
        fun added(obj: O, idx: Int)
        fun removed(obj: O)
        fun moved(obj: O, idx: Int) {
        }
    }
}