package xenakis.ui.flow

import fxutils.actions.ActionBar
import fxutils.actions.action
import fxutils.centerChildren
import fxutils.setFixedWidth
import fxutils.setupDropArea
import javafx.scene.layout.HBox
import org.kordamp.ikonli.materialdesign2.MaterialDesignD
import xenakis.model.flow.AudioFlowGroup
import xenakis.model.flow.AudioFlows
import xenakis.ui.controls.NameControl
import xenakis.ui.impl.colorPicker
import xenakis.ui.registry.ObjectListView
import xenakis.ui.registry.ObjectListView.Companion.modeChangeActions
import xenakis.ui.registry.ToolPane

class FlowGroupPane(group: AudioFlowGroup) : ToolPane() {
    private val config = FlowListConfig(group.context, autoResizeScene = true)
    private val flowsView = ObjectListView(group.flows, config)

    init {
        flowsView.setupDropArea(config::canDrop) { ev -> config.onDrop(ev, flowsView) }
        val nameControl = NameControl(group)
        val colorPicker = colorPicker(group.associatedColor).setFixedWidth(30.0)
        val actions = AudioFlowPane.actions.withContext(group) + removeAction.withContext(group)
        val actionBar = ActionBar(actions, buttonStyle = "medium-icon-button")
        val headerContent = HBox(5.0, nameControl, colorPicker, actionBar).centerChildren()
        flowsView.autoResizeScene = true
        val windowActions = modeChangeActions.withContext(flowsView)
        setup(flowsView, title = null, headerContent, windowActions)
    }

    companion object {
        private val removeAction = action<AudioFlowGroup>("Remove") {
            icon(MaterialDesignD.DELETE)
            executes { group -> group.context[AudioFlows].remove(group) }
        }
    }
}