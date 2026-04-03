package ponticello.model.ctx

import reaktive.value.ReactiveString
import reaktive.value.reactiveValue

class SingleLetterVariable(override val origin: Char) : BoundVariable() {
    override val name: ReactiveString
        get() = reactiveValue(origin.toString())
    override val info: ReactiveString
        get() = reactiveValue("Global")
}