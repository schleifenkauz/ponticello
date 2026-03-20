package ponticello.model.project

import bundles.PublicProperty
import bundles.publicProperty
import bundles.set
import fxutils.prompt.PromptPlacement
import hextant.context.Context
import hextant.fx.ModifierKeyTracker
import kotlinx.serialization.Serializable
import ponticello.impl.DecimalRange
import ponticello.model.instr.InstrumentObject
import ponticello.model.instr.InstrumentRegistry
import ponticello.model.obj.AbstractContextualObject
import ponticello.model.registry.ObjectReference
import ponticello.model.registry.reference
import ponticello.model.score.TimeUnit
import ponticello.ui.dock.Side
import ponticello.ui.dock.SideBarState
import ponticello.ui.dock.ToolPaneState
import ponticello.ui.launcher.PonticelloMainActivity
import ponticello.ui.registry.SimpleRegistrySelectorPrompt
import reaktive.value.ReactiveVariable
import reaktive.value.now
import reaktive.value.reactiveVariable

@Serializable
class UIState private constructor(
    val snapEnabled: ReactiveVariable<Boolean> = reactiveVariable(false),
    val snapOption: ReactiveVariable<TimeUnit> = reactiveVariable(TimeUnit.Seconds),
    val selectedInstrument: ReactiveVariable<ObjectReference<InstrumentObject>?> = reactiveVariable(null),
    val askForCloneNames: ReactiveVariable<Boolean> = reactiveVariable(false),
    val askForGroupNames: ReactiveVariable<Boolean> = reactiveVariable(false),
    val controlsDisplay: ReactiveVariable<InlineControlsDisplay> = reactiveVariable(InlineControlsDisplay.NONE),
    var mainScoreDisplayRange: DecimalRange? = null,
    var toolPaneStates: List<ToolPaneState> = emptyList(),
    var sideBarStates: List<SideBarState> = emptyList(),
    private val windowStates: MutableList<WindowState> = mutableListOf(),
) : AbstractContextualObject() {
    override fun initialize(context: Context) {
        super.initialize(context)
        selectedInstrument.now?.resolve(context[InstrumentRegistry])
        context[UIState] = this
    }

    fun getOrSelectInstrument(promptPlacement: PromptPlacement): InstrumentObject? =
        selectedInstrument.get()?.get().takeIf { !ModifierKeyTracker.isShiftDown.now } ?: selectInstrument(
            promptPlacement
        )

    fun selectInstrument(promptPlacement: PromptPlacement): InstrumentObject? {
        val instrument = SimpleRegistrySelectorPrompt(context[InstrumentRegistry], "Select instrument")
            .showPopup(promptPlacement) ?: return null
        selectedInstrument.now = instrument.reference()
        return instrument
    }

    fun getSideBarState(side: Side) = sideBarStates.find { state -> state.side == side }

    fun getWindowState(reference: WindowState.Reference, default: (WindowState.Reference) -> WindowState): WindowState {
        val present = windowStates.find { state -> state.reference == reference }
        if (present != null) return present
        val newState = default(reference)
        windowStates.add(newState)
        return newState
    }

    fun saveWindowStates() {
        if (context.hasProperty(PonticelloMainActivity)) {
            mainScoreDisplayRange = context[PonticelloMainActivity].mainScoreView.timeRange
        }
        for (state in windowStates) {
            state.saveFromTarget()
        }
    }

    companion object : PublicProperty<UIState> by publicProperty("UIState") {
        fun default() = UIState()
    }
}