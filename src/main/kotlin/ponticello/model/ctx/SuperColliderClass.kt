package ponticello.model.ctx

import org.kordamp.ikonli.Ikon
import org.kordamp.ikonli.materialdesign2.MaterialDesignA
import reaktive.value.ReactiveString
import reaktive.value.reactiveValue

data class SuperColliderClass(private val className: String) : BoundVariable() {
    override val origin: Any
        get() = className
    override val name: ReactiveString get() = reactiveValue(className)
    override val info: ReactiveString get() = reactiveValue("Class")
    override val icon: Ikon get() = MaterialDesignA.ALPHA_C_CIRCLE
}