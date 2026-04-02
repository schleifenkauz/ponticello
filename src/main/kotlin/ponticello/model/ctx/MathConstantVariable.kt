package ponticello.model.ctx

import reaktive.value.ReactiveString
import reaktive.value.reactiveValue

data class MathConstantVariable(override val origin: String) : BoundVariable() {
    override val name: ReactiveString
        get() = reactiveValue(origin)
    override val info: ReactiveString
        get() = reactiveValue("Constant")
}