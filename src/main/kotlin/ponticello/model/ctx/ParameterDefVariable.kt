package ponticello.model.ctx

import org.kordamp.ikonli.Ikon
import org.kordamp.ikonli.materialdesign2.MaterialDesignA
import ponticello.model.instr.ParameterDefObject
import reaktive.value.ReactiveString
import reaktive.value.binding.map

class ParameterDefVariable(override val origin: ParameterDefObject) : Scope.BoundVariable() {
    override val name: ReactiveString
        get() = origin.name
    override val info: ReactiveString
        get() = origin.spec.map { spec -> spec.type.name.lowercase() }

    override val icon: Ikon get() = MaterialDesignA.ALPHA_P_CIRCLE
}