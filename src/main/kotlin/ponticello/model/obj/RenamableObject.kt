package ponticello.model.obj

import ponticello.model.registry.NamedObject

interface RenamableObject : NamedObject {
    fun canRenameTo(newName: String): Boolean

    fun rename(newName: String)

    val canRename: Boolean get() = true
}