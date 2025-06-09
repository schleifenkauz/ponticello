package ponticello.model.registry

import ponticello.model.obj.project

abstract class ObjectRegistry<O : NamedObject>: NamedObjectList<O>() {
    fun save() {
        context.project.save(this)
    }

    fun availableName(prefix: String): String {
        for (n in 1..Int.MAX_VALUE) {
            val name = "${prefix}_$n"
            if (!has(name)) return name
        }
        throw AssertionError()
    }

    open fun getDefault(): O? = null

    open fun syncAll() {}
}