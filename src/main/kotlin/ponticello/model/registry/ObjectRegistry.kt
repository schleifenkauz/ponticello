package ponticello.model.registry

abstract class ObjectRegistry<O : NamedObject> : NamedObjectList<O>() {
    open fun getDefault(): O? = null

    open fun syncAll() {}
}