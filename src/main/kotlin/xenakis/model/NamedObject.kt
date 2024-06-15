package xenakis.model

import hextant.context.Context
import reaktive.value.ReactiveValue

interface NamedObject {
    val name: ReactiveValue<String>

    fun onAdded(context: Context)

    fun initialize(context: Context)

    fun onRemoved()

    fun createReference(): ObjectReference<*>
}