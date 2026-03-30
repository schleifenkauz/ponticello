package ponticello.model.ctx

import org.kordamp.ikonli.Ikon
import org.kordamp.ikonli.materialdesign2.MaterialDesignA
import ponticello.model.score.controls.ParameterControlList.NamedParameterControl
import reaktive.value.ReactiveString
import reaktive.value.binding.map

data class ParameterControlVariable(override val origin: NamedParameterControl) : BoundVariable() {
    override val name: ReactiveString get() = origin.name

    override val icon: Ikon get() = MaterialDesignA.ALPHA_P_CIRCLE

    override val info: ReactiveString
        get() = origin.spec.map { it?.type?.name ?: "unknown" }
}