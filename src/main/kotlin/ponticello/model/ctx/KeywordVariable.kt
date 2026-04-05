package ponticello.model.ctx

import org.kordamp.ikonli.Ikon
import org.kordamp.ikonli.materialdesign2.MaterialDesignA
import reaktive.value.ReactiveString
import reaktive.value.reactiveValue

data class KeywordVariable(override val origin: String) : BoundVariable() {
    override val name: ReactiveString
        get() = reactiveValue(origin)
    override val info: ReactiveString
        get() = reactiveValue("")

    override val icon: Ikon
        get() = MaterialDesignA.ALPHA_I_CIRCLE
}

