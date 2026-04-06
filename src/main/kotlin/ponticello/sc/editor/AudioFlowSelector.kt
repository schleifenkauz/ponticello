package ponticello.sc.editor

import ponticello.model.flow.AudioFlow
import ponticello.model.obj.project
import ponticello.model.project.flows
import ponticello.ui.dock.AppLayout
import ponticello.ui.flow.TabbedAudioFlowsPane

class AudioFlowSelector : ObjectSelector<AudioFlow>() {
    override val objectType: String
        get() = "Flow"

    override fun getOptions(): List<AudioFlow> = context.project.flows.allFlows()

    override val canViewSelected: Boolean get() = true

    override fun viewObject(obj: AudioFlow) {
        val flowsPane = context[AppLayout].get<TabbedAudioFlowsPane>()
        flowsPane.setShowing(true)
        flowsPane.select(obj.parentGroup)
    }
}