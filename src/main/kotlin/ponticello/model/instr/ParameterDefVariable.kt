package ponticello.model.instr

import ponticello.model.ctx.Scope
import reaktive.value.ReactiveString
import reaktive.value.binding.map

class ParameterDefVariable(override val origin: ParameterDefObject) : Scope.BoundVariable() {
    override val name: ReactiveString
        get() = origin.name
    override val info: ReactiveString
        get() = origin.spec.map { spec -> spec.type.name.lowercase() }
}