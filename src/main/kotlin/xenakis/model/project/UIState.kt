package xenakis.model.project

import kotlinx.serialization.Serializable
import reaktive.value.ReactiveVariable
import reaktive.value.reactiveVariable
import xenakis.model.obj.AbstractContextualObject

@Serializable
class UIState(
    val snapEnabled: ReactiveVariable<Boolean>,
    val snapOption: ReactiveVariable<SnapOption>,
    val displayTimeGrid: ReactiveVariable<Boolean>,
    val windowStates: MutableMap<String, WindowState> = mutableMapOf()
) : AbstractContextualObject() {
    enum class SnapOption {
        Seconds, Bars, Beats, Ticks;
    }

    fun saveWindowStates() {
        for ((_, state) in windowStates) {
            state.saveFromTarget()
        }
    }

    companion object {
        fun default() = UIState(
            reactiveVariable(false),
            reactiveVariable(SnapOption.Seconds),
            reactiveVariable(false)
        )
    }
}