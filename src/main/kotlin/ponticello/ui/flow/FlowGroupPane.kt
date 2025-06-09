package ponticello.ui.flow

import fxutils.actions.ActionBar
import fxutils.actions.action
import fxutils.centerChildren
import fxutils.infiniteSpace
import fxutils.setFixedWidth
import fxutils.setupDropArea
import javafx.scene.layout.HBox
import javafx.scene.layout.VBox
import org.kordamp.ikonli.materialdesign2.MaterialDesignD
import ponticello.model.flow.AudioFlowGroup
import ponticello.model.flow.AudioFlows
import ponticello.ui.controls.NameControl
import ponticello.ui.impl.colorPicker
import ponticello.ui.registry.ObjectListView

class FlowGroupPane(group: AudioFlowGroup, ownWindow: Boolean): VBox() {
    private val config = FlowListConfig(group, autoResizeScene = true)
    val flowsView = ObjectListView(group.flows, config)

    init {
        flowsView.itemsScrollPane.setupDropArea(config::canDrop, { ev -> config.onDrop(ev, flowsView) })
        if (ownWindow) {
            val nameControl = NameControl(group).setFixedWidth(150.0)
            val colorPicker = colorPicker(group.associatedColor).setFixedWidth(30.0)
            val actions = AudioFlowPane.actions.withContext(group) + removeAction.withContext(group)
            val actionBar = ActionBar(actions, buttonStyle = "medium-icon-button")
            val header = HBox(5.0, nameControl, colorPicker, infiniteSpace(), actionBar).centerChildren()
            flowsView.autoResizeScene = true
            children.addAll(header, flowsView)
        } else {
            children.add(flowsView)
        }
    }

    companion object {
        private val removeAction = action<AudioFlowGroup>("Remove") {
            icon(MaterialDesignD.DELETE)
            executes { group -> group.context[AudioFlows].remove(group) }
        }
    }
}