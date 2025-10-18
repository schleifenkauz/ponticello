package ponticello.model.player

import kotlinx.serialization.Serializable
import ponticello.impl.Decimal
import ponticello.impl.toDecimal
import reaktive.value.ReactiveVariable
import reaktive.value.reactiveVariable

@Serializable
data class ConductorOptions(
    val port: ReactiveVariable<Int> = reactiveVariable(57140),
    val extraArguments: ReactiveVariable<String> = reactiveVariable(""),
    val countdownTime: ReactiveVariable<Int> = reactiveVariable(5),
    val beatThreshold: ReactiveVariable<Decimal> = reactiveVariable(0.2.toDecimal()),
    val warpFactor: ReactiveVariable<Decimal> = reactiveVariable(1.toDecimal()),
    val modelName: ReactiveVariable<String> = reactiveVariable("<none>"),
    val videoInput: ReactiveVariable<String> = reactiveVariable("/dev/video0")
) {
    companion object {
        fun createDefault() = ConductorOptions()
    }
}