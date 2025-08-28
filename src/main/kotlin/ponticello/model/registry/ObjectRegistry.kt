package ponticello.model.registry

import ponticello.model.obj.project

abstract class ObjectRegistry<O : NamedObject>: NamedObjectList<O>() {
    fun save() {
        context.project.save(this)
    }

    open fun getDefault(): O? = null

    open fun syncAll() {}
}