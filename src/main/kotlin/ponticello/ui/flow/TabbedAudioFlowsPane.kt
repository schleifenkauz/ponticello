package ponticello.ui.flow

import fxutils.actions.ActionBar
import fxutils.actions.collectActions
import fxutils.actions.makeButton
import fxutils.pad
import fxutils.setFixedWidth
import fxutils.setPseudoClassState
import javafx.scene.Node
import javafx.scene.Parent
import javafx.scene.layout.HBox
import org.kordamp.ikonli.Ikon
import org.kordamp.ikonli.materialdesign2.MaterialDesignC
import org.kordamp.ikonli.materialdesign2.MaterialDesignT
import ponticello.impl.Logger
import ponticello.model.flow.AudioFlowGroup
import ponticello.model.flow.AudioFlows
import ponticello.model.project.PonticelloProject
import ponticello.model.project.flows
import ponticello.ui.controls.NameControl
import ponticello.ui.dock.*
import ponticello.ui.impl.colorPicker
import ponticello.ui.registry.ObjectListView

//TODO how to group by bus?
class TabbedAudioFlowsPane(private val flows: AudioFlows) : TabbedToolPane<AudioFlowGroup>(flows) {
    override val type: Type
        get() = TabbedAudioFlowsPane

    override fun getContent(obj: AudioFlowGroup): Parent = FlowGroupPane(obj, null).flowsView

    override fun defaultState(): ToolPaneState = TabbedFlowPaneState.default()

    override fun createItemBox(obj: AudioFlowGroup): Node {
        val nameControl = NameControl(obj)
        val toggleBtn = FlowGroupPane.toggleActiveAction.withContext(obj).makeButton("small-icon-button")
        val colorPicker = colorPicker(obj.associatedColor).setFixedWidth(30.0)
        val actionBar = ActionBar(groupActions.withContext(Pair(flows, obj)), "small-icon-button")
        return HBox(3.0, toggleBtn, nameControl, colorPicker, actionBar).pad(3.0)
    }

    fun setHover(group: AudioFlowGroup, value: Boolean) {
        getItemBox(group)?.setPseudoClassState("flow-group-hover", value)
    }

    private fun allFlowBoxes() = flows.flatMap { flow -> (content(flow) as ObjectListView<*>).getBoxes() }

    override fun saveState(dest: ToolPaneState) {
        super.saveState(dest)
        if (dest is TabbedFlowPaneState && isSetup) {
            dest.expandedFlows = allFlowBoxes().withIndex()
                .filter { (_, box) -> box.isExpanded }
                .map { v -> v.index }
        }

    }

    override fun afterSetup() {
        super.afterSetup()
        val state = initialState
        if (state is TabbedFlowPaneState) {
            val flowBoxes = allFlowBoxes()
            for (idx in state.expandedFlows) {
                val box = flowBoxes.getOrNull(idx)
                if (box == null) {
                    Logger.warn("AudioFlow at index $idx not found", Logger.Category.Registries)
                    continue
                }
                box.toggleExpanded()
            }
        }
    }

    companion object : Type(uid = 13, "Flows") {
        override val icon: Ikon get() = MaterialDesignT.TUNE

        override val defaultSide: Side
            get() = Side.BOTTOM

        override val shortcut: String get() = "F9"

        override fun createToolPane(project: PonticelloProject): ToolPane = TabbedAudioFlowsPane(project.flows)

        private val groupActions = collectActions<Pair<AudioFlows, AudioFlowGroup>> {
            addAction("Remove group") {
                icon(MaterialDesignC.CLOSE_CIRCLE_OUTLINE)
                executes { (flows, grp) -> flows.remove(grp) }
            }
        }
    }
}