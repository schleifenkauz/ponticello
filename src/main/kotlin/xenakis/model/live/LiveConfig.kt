package xenakis.model.live

import kotlinx.serialization.Serializable
import reaktive.value.ReactiveVariable
import reaktive.value.reactiveVariable
import xenakis.impl.Decimal
import xenakis.impl.zero

@Serializable
class LiveConfig(
    val yPosition: ReactiveVariable<Decimal> = reactiveVariable(zero),
    val addToScore: ReactiveVariable<Boolean> = reactiveVariable(false),
    val loop: ReactiveVariable<Boolean> = reactiveVariable(false)
) {
    companion object {
        fun createDefault(): LiveConfig = LiveConfig()
    }
}