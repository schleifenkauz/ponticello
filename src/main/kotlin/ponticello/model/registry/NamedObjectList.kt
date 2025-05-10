package ponticello.model.registry

import hextant.context.Context
import reaktive.value.now
import ponticello.impl.Logger

abstract class NamedObjectList<O : NamedObject> : ObjectList<O>() {
    override fun initialize(context: Context) {
        super.initialize(context)
        for (obj in this) {
            obj.onLoadedIntoRegistry()
        }
    }

    open fun getOrNull(name: String) = objects.find { it.name.now == name }

    fun get(name: String): O = getOrNull(name) ?: throw NoSuchElementException("Object $name not found in $this")

    fun has(name: String) = objects.any { it.name.now == name }

    fun overwrite(obj: O) {
        val old = get(obj.name.now)
        val index = objects.indexOf(old)
        remove(old)
        add(obj, index)
    }

    override fun add(obj: O, idx: Int) {
        if (obj.name.now != NamedObject.NO_NAME && has(obj.name.now)) {
            Logger.severe("$objectType with name ${obj.name.now} already registered.", Logger.Category.Registries)
            return
        }
        super.add(obj, idx)
        obj.onAdded()
        obj.onLoadedIntoRegistry()
    }

    override fun remove(obj: O) {
        super.remove(obj)
        obj.onRemoved()
    }

    fun removeByName(name: String) {
        val control = getOrNull(name) ?: error("$objectType with name '$name' not found in $this")
        remove(control)
    }
}