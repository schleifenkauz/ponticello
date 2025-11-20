package ponticello.model.registry

import ponticello.model.obj.NamedObject

fun <O : NamedObject> O.reference(): ObjectReference<O> {
    return ObjectReference(this)
}