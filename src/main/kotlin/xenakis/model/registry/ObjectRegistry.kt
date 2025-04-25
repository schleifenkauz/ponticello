package xenakis.model.registry

import xenakis.ui.launcher.XenakisLauncher.Companion.currentProject

abstract class ObjectRegistry<O : NamedObject>: NamedObjectList<O>() {
    fun save() {
        context[currentProject].save(this)
    }

    open fun getDefault(): O? = null

    abstract fun syncAll()
}