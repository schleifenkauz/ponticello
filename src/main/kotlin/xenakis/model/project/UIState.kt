package xenakis.model.project

import hextant.context.Context
import javafx.event.Event
import kotlinx.serialization.Serializable
import reaktive.value.ReactiveVariable
import reaktive.value.now
import reaktive.value.reactiveVariable
import xenakis.model.obj.AbstractContextualObject
import xenakis.model.obj.SynthDefObject
import xenakis.model.obj.SynthDefReference
import xenakis.model.registry.SynthDefRegistry
import xenakis.model.registry.reference
import xenakis.model.score.TimeUnit
import xenakis.ui.registry.SimpleSearchableRegistryView

@Serializable
class UIState private constructor(
    val snapEnabled: ReactiveVariable<Boolean> = reactiveVariable(false),
    val snapOption: ReactiveVariable<TimeUnit> = reactiveVariable(TimeUnit.Seconds),
    val selectedSynthDef: ReactiveVariable<SynthDefReference?> = reactiveVariable(null),
    val windowStates: MutableMap<String, WindowState> = mutableMapOf(),
) : AbstractContextualObject() {
    override fun initialize(context: Context) {
        super.initialize(context)
        selectedSynthDef.now?.resolve(context[SynthDefRegistry])
    }

    fun getOrSelectSynthDef(event: Event?): SynthDefObject? =
        selectedSynthDef.get()?.get() ?: selectSynthDef(event)

    fun selectSynthDef(event: Event?): SynthDefObject? {
        val synthDef = SimpleSearchableRegistryView(context[SynthDefRegistry], "Select instrument")
            .showPopup(event) ?: return null
        selectedSynthDef.now = synthDef.reference()
        return synthDef
    }

    fun saveWindowStates() {
        for ((_, state) in windowStates) {
            state.saveFromTarget()
        }
    }

    companion object {
        fun default() = UIState()
    }
}