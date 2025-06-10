package ponticello.model.project

import bundles.PublicProperty
import bundles.publicProperty
import bundles.set
import hextant.context.Context
import javafx.event.Event
import kotlinx.serialization.Serializable
import ponticello.model.obj.AbstractContextualObject
import ponticello.model.obj.InstrumentObject
import ponticello.model.obj.InstrumentReference
import ponticello.model.registry.InstrumentRegistry
import ponticello.model.registry.reference
import ponticello.model.score.TimeUnit
import ponticello.ui.dock.Side
import ponticello.ui.dock.ToolPaneState
import ponticello.ui.dock.WindowBounds
import ponticello.ui.registry.SimpleSearchableRegistryView
import reaktive.value.ReactiveVariable
import reaktive.value.now
import reaktive.value.reactiveVariable

@Serializable
class UIState private constructor(
    val mainWindowBounds: WindowBounds? = null,
    val snapEnabled: ReactiveVariable<Boolean> = reactiveVariable(false),
    val snapOption: ReactiveVariable<TimeUnit> = reactiveVariable(TimeUnit.Seconds),
    val selectedInstrument: ReactiveVariable<InstrumentReference?> = reactiveVariable(null),
    val askForCloneNames: ReactiveVariable<Boolean> = reactiveVariable(false),
    val askForGroupNames: ReactiveVariable<Boolean> = reactiveVariable(false),
    val controlsDisplay: ReactiveVariable<InlineControlsDisplay> = reactiveVariable(InlineControlsDisplay.NONE),
    var toolPaneStates: List<ToolPaneState> = emptyList(),
    var sideBars: Map<Side, List<Int>> = emptyMap(),
    var dividerPositions: Map<Side, Double> = emptyMap(),
    private val windowStates: MutableList<WindowState> = mutableListOf(),
) : AbstractContextualObject() {
    override fun initialize(context: Context) {
        super.initialize(context)
        selectedInstrument.now?.resolve(context[InstrumentRegistry])
        context[UIState] = this
    }

    fun getOrSelectInstrument(event: Event?): InstrumentObject? =
        selectedInstrument.get()?.get() ?: selectInstrument(event)

    fun selectInstrument(event: Event?): InstrumentObject? {
        val instrument = SimpleSearchableRegistryView(context[InstrumentRegistry], "Select instrument")
            .showPopup(event) ?: return null
        selectedInstrument.now = instrument.reference()
        return instrument
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

    companion object : PublicProperty<UIState> by publicProperty("UIState") {
        fun default() = UIState(
            controlsDisplay = reactiveVariable(InlineControlsDisplay.EXTENDED_OVERLAY),

        )
    }
}