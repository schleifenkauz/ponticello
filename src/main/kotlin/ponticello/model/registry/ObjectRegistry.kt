package ponticello.model.registry

import ponticello.ui.launcher.PonticelloLauncher.Companion.currentProject

abstract class ObjectRegistry<O : NamedObject>: NamedObjectList<O>() {
    fun save() {
        context[currentProject].save(this)
    }

    fun availableName(prefix: String): String {
        for (n in 1..Int.MAX_VALUE) {
            val name = "${prefix}_$n"
            if (!has(name)) return name
        }
        throw AssertionError()
    }

    open fun getDefault(): O? = null

    abstract fun syncAll()
}