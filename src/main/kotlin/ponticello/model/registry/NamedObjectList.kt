package ponticello.model.registry

import hextant.context.Context
import ponticello.impl.Logger
import ponticello.model.obj.NamedObject
import reaktive.value.now

abstract class NamedObjectList<O : NamedObject> : ObjectList<O>() {
    override fun initialize(context: Context) {
        super.initialize(context)
        for (obj in this) {
            obj.activate()
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
        obj.activate()
    }

    override fun remove(obj: O) {
        removeByName(obj.name.now)
        try {
            obj.deactivate()
        } catch (e: Exception) {
            Logger.error("Error while deactivating $objectType '${obj.name.now}'", e)
        }
        try {
            obj.dispose()
        } catch (e: Exception) {
            Logger.error("Error while disposing $objectType '${obj.name.now}'", e)
        }
    }

    fun removeByName(name: String) {
        val control = getOrNull(name) ?: error("$objectType with name '$name' not found in $this")
        super.remove(control)
    }

    fun availableName(prefix: String): String {
        for (n in 1..Int.MAX_VALUE) {
            val name = "${prefix}_$n"
            if (!has(name)) return name
        }
        throw AssertionError()
    }

    override fun dispose() {
        for (obj in objects) {
            try {
                obj.dispose()
            } catch (e: Exception) {
                Logger.error("Error while disposing $objectType '${obj.name.now}'", e)
            }
        }
    }
}