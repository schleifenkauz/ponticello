package ponticello.sc.editor

import ponticello.model.flow.MidiTrackFlow
import ponticello.model.obj.project
import ponticello.model.project.flows
import ponticello.ui.dock.AppLayout
import ponticello.ui.flow.TabbedAudioFlowsPane

class MidiTrackSelector : ObjectSelector<MidiTrackFlow>() {
    override val objectType: String
        get() = "Midi Track"

    override fun getOptions(): List<MidiTrackFlow> = context.project.flows.allMidiTracks()

    override val canViewSelected: Boolean get() = true

    override fun viewObject(obj: MidiTrackFlow) {
        val flowsPane = context[AppLayout].get<TabbedAudioFlowsPane>()
        flowsPane.setShowing(true)
        flowsPane.select(obj.parentGroup)
    }
}