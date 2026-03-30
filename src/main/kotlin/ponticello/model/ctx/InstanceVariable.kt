package ponticello.model.ctx

import org.kordamp.ikonli.Ikon
import org.kordamp.ikonli.materialdesign2.MaterialDesignA
import reaktive.value.ReactiveString
import reaktive.value.reactiveValue

object InstanceVariable : Scope.BoundVariable() {
    override val origin: Any
        get() = 0
    override val name: ReactiveString
        get() = reactiveValue("inst")
    override val info: ReactiveString
        get() = reactiveValue("")

    override val icon: Ikon
        get() = MaterialDesignA.ALPHA_I_CIRCLE
}

