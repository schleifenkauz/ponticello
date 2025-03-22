package xenakis.model.registry

import hextant.context.Context
import xenakis.ui.launcher.XenakisLauncher.Companion.currentProject

abstract class ObjectRegistry<O : NamedObject>: NamedObjectList<O>() {
    override fun initialize(context: Context) {
        super.initialize(context)
        for (obj in this) {
            obj.onLoadedIntoRegistry()
        }
    }

    fun save() {
        context[currentProject].save(this)
    }

    open fun getDefault(): O? = null
}