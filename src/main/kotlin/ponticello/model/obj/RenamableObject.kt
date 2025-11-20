package ponticello.model.obj

interface RenamableObject : NamedObject {
    fun setInitialName(name: String)

    fun canRenameTo(newName: String): Boolean

    fun rename(newName: String)

    val canRename: Boolean get() = true

    override fun copy(): RenamableObject
}