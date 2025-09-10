package ponticello.model.player

import kotlinx.serialization.Serializable
import reaktive.value.ReactiveVariable
import reaktive.value.reactiveVariable

@Serializable
data class ConductorOptions(
    val port: ReactiveVariable<Int> = reactiveVariable(57140),
    val extraArguments: ReactiveVariable<String> = reactiveVariable(""),
    val countdownTime: ReactiveVariable<Int> = reactiveVariable(5)
) {
    companion object {
        fun createDefault() = ConductorOptions()
    }
}