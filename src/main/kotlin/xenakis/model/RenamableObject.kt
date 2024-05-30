package xenakis.model

import reaktive.value.ReactiveValue

interface RenamableObject {
    val name: ReactiveValue<String>

    fun canRenameTo(newName: String): Boolean

    fun rename(newName: String)
}