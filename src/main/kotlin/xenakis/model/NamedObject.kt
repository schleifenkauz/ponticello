package xenakis.model

import hextant.context.Context
import reaktive.value.ReactiveValue

interface NamedObject {
    val name: ReactiveValue<String>

    fun initialize(context: Context)

    fun remove()

    fun createReference(): ObjectReference<*>
}