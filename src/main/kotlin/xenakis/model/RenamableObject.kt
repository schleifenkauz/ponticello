package xenakis.model

interface RenamableObject : NamedObject {
    fun canRenameTo(newName: String): Boolean

    fun rename(newName: String)
}