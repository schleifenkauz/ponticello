package ponticello.model.registry

import ponticello.model.obj.NamedObject

abstract class ObjectRegistry<O : NamedObject> : NamedObjectList<O>() {
    open fun getDefault(): O? = null

    open fun syncAll() {}
}