package xenakis.model

import kotlinx.serialization.Serializable
import reaktive.value.ReactiveVariable
import reaktive.value.reactiveVariable

@Serializable
class InteractionSettings(
    val snapEnabled: ReactiveVariable<Boolean>,
    val snapOption: ReactiveVariable<SnapOption>,
    val displayTimeGrid: ReactiveVariable<Boolean>
) {
    enum class SnapOption {
        Seconds, Bars, Beats, Ticks;
    }

    companion object {
        fun default() = InteractionSettings(
            reactiveVariable(false),
            reactiveVariable(SnapOption.Seconds),
            reactiveVariable(false)
        )
    }
}