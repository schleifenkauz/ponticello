package xenakis.model.project

import hextant.context.Context
import javafx.geometry.Point2D
import javafx.stage.Window
import kotlinx.serialization.Serializable
import reaktive.value.ReactiveVariable
import reaktive.value.now
import reaktive.value.reactiveVariable
import xenakis.model.obj.AbstractContextualObject
import xenakis.model.obj.SynthDefObject
import xenakis.model.obj.SynthDefReference
import xenakis.model.registry.SynthDefRegistry
import xenakis.model.registry.reference
import xenakis.ui.registry.SimpleSearchableRegistryView

@Serializable
class UIState private constructor(
    val snapEnabled: ReactiveVariable<Boolean> = reactiveVariable(false),
    val snapOption: ReactiveVariable<SnapOption> = reactiveVariable(SnapOption.Seconds),
    val displayTimeGrid: ReactiveVariable<Boolean> = reactiveVariable(false),
    val selectedSynthDef: ReactiveVariable<SynthDefReference?> = reactiveVariable(null),
    val windowStates: MutableMap<String, WindowState> = mutableMapOf(),
) : AbstractContextualObject() {
    override fun initialize(context: Context) {
        super.initialize(context)
        selectedSynthDef.now?.resolve(context[SynthDefRegistry])
    }

    fun getOrSelectSynthDef(anchor: Point2D, ownerWindow: Window): SynthDefObject? =
        selectedSynthDef.get()?.get() ?: selectSynthDef(anchor, ownerWindow)

    fun selectSynthDef(anchor: Point2D, ownerWindow: Window): SynthDefObject? {
        val synthDef = SimpleSearchableRegistryView(context[SynthDefRegistry], "Select instrument")
            .showPopup(anchor, owner = ownerWindow) ?: return null
        selectedSynthDef.now = synthDef.reference()
        return synthDef
    }

    fun saveWindowStates() {
        for ((_, state) in windowStates) {
            state.saveFromTarget()
        }
    }

    enum class SnapOption {
        Seconds, Bars, Beats, Ticks;
    }

    companion object {
        fun default() = UIState()
    }
}