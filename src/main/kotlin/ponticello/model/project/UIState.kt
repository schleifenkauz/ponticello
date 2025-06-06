package ponticello.model.project

import bundles.PublicProperty
import bundles.publicProperty
import bundles.set
import hextant.context.Context
import javafx.event.Event
import kotlinx.serialization.Serializable
import ponticello.model.obj.AbstractContextualObject
import ponticello.model.obj.SynthDefObject
import ponticello.model.obj.SynthDefReference
import ponticello.model.registry.SynthDefRegistry
import ponticello.model.registry.reference
import ponticello.model.score.TimeUnit
import ponticello.ui.registry.SimpleSearchableRegistryView
import reaktive.value.ReactiveVariable
import reaktive.value.now
import reaktive.value.reactiveVariable

@Serializable
class UIState private constructor(
    val snapEnabled: ReactiveVariable<Boolean> = reactiveVariable(false),
    val snapOption: ReactiveVariable<TimeUnit> = reactiveVariable(TimeUnit.Seconds),
    val selectedSynthDef: ReactiveVariable<SynthDefReference?> = reactiveVariable(null),
    val askForCloneNames: ReactiveVariable<Boolean> = reactiveVariable(false),
    val askForGroupNames: ReactiveVariable<Boolean> = reactiveVariable(false),
    private val windowStates: MutableList<WindowState> = mutableListOf(),
) : AbstractContextualObject() {
    override fun initialize(context: Context) {
        super.initialize(context)
        selectedSynthDef.now?.resolve(context[SynthDefRegistry])
        context[UIState] = this
    }

    fun getOrSelectSynthDef(event: Event?): SynthDefObject? =
        selectedSynthDef.get()?.get() ?: selectSynthDef(event)

    fun selectSynthDef(event: Event?): SynthDefObject? {
        val synthDef = SimpleSearchableRegistryView(context[SynthDefRegistry], "Select instrument")
            .showPopup(event) ?: return null
        selectedSynthDef.now = synthDef.reference()
        return synthDef
    }

    fun getWindowState(reference: WindowState.Reference, default: (WindowState.Reference) -> WindowState): WindowState {
        val present = windowStates.find { state -> state.reference == reference }
        if (present != null) return present
        val newState = default(reference)
        windowStates.add(newState)
        return newState
    }

    fun saveWindowStates() {
        for (state in windowStates) {
            state.saveFromTarget()
        }
    }

    companion object: PublicProperty<UIState> by publicProperty("UIState") {
        fun default() = UIState()
    }
}