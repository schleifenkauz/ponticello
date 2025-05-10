package ponticello.model.registry

import ponticello.ui.launcher.PonticelloLauncher.Companion.currentProject

abstract class ObjectRegistry<O : NamedObject>: NamedObjectList<O>() {
    fun save() {
        context[currentProject].save(this)
    }

    open fun getDefault(): O? = null

    abstract fun syncAll()
}