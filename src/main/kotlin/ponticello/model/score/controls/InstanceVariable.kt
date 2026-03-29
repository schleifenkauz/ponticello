package ponticello.model.score.controls

import ponticello.model.ctx.Scope
import reaktive.value.ReactiveString
import reaktive.value.reactiveValue

object InstanceVariable : Scope.BoundVariable() {
    override val origin: Any
        get() = 0
    override val name: ReactiveString
        get() = reactiveValue("inst")
    override val info: ReactiveString
        get() = reactiveValue("")
}