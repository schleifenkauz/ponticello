package ponticello.model.live

import kotlinx.serialization.Serializable
import ponticello.impl.Decimal
import ponticello.impl.zero
import reaktive.value.ReactiveVariable
import reaktive.value.reactiveVariable

@Serializable
class LiveConfig(
    val yPosition: ReactiveVariable<Decimal> = reactiveVariable(zero),
    val loop: ReactiveVariable<Boolean> = reactiveVariable(false)
) {
    companion object {
        fun createDefault(): LiveConfig = LiveConfig()
    }
}