package ponticello.ui.flow

import fxutils.actions.ActionBar
import fxutils.actions.collectActions
import fxutils.popupAnchor
import fxutils.prompt.SimpleSearchableListView
import fxutils.prompt.TextPrompt
import fxutils.setFixedWidth
import javafx.geometry.Orientation
import javafx.scene.Node
import javafx.scene.Parent
import org.kordamp.ikonli.Ikon
import org.kordamp.ikonli.materialdesign2.MaterialDesignP
import org.kordamp.ikonli.materialdesign2.MaterialDesignR
import org.kordamp.ikonli.materialdesign2.MaterialDesignT
import ponticello.model.flow.AudioFlowGroup
import ponticello.model.flow.AudioFlows
import ponticello.model.project.PonticelloProject
import ponticello.model.project.flows
import ponticello.sc.Identifier
import ponticello.ui.dock.*
import ponticello.ui.impl.colorPicker
import ponticello.ui.registry.ObjectListView.DisplayMode
import reaktive.value.binding.`if`
import reaktive.value.binding.map
import reaktive.value.now

class AudioFlowPane(flows: AudioFlows) : SearchableToolPane<AudioFlowGroup>(flows) {
    override val type: Type
        get() = AudioFlowPane

    override val inlineOrientation: Orientation
        get() = Orientation.HORIZONTAL

    override val supportedModes: Set<DisplayMode>
        get() = setOf(DisplayMode.Inline(collapsable = false), DisplayMode.SubWindow, DisplayMode.DetailsPane)

    init {
        styleClass.add("flow-pane")
    }

    override fun defaultState(): ToolPaneState = FlowPaneState()

    override fun afterSetup() {
        super.afterSetup()
        listView.itemsScrollPane.isFitToHeight = true
        listView.autoResizeScene = true
        val state = initialState
        if (state is FlowPaneState) {
            val flowBoxes = allFlowBoxes()
            for (idx in state.expandedFlows) {
                flowBoxes[idx].toggleExpanded()
            }
        }
    }

    override fun getItemContent(obj: AudioFlowGroup): List<Node> {
        val colorPicker = colorPicker(obj.associatedColor).setFixedWidth(30.0)
        val actionBar = ActionBar(actions.withContext(obj), "medium-icon-button")
        return listOf(colorPicker, actionBar)
    }

    override fun getContent(obj: AudioFlowGroup, mode: DisplayMode): Parent =
        FlowGroupPane(obj, ownWindow = mode == DisplayMode.SubWindow)

    override fun onDeselected(obj: AudioFlowGroup) {
        val groupPane = listView.getBox(obj).content as? FlowGroupPane ?: return
        groupPane.flowsView.deselectAll()
    }

    override fun saveState(dest: ToolPaneState) {
        super.saveState(dest)
        if (dest is FlowPaneState) {
            dest.expandedFlows = allFlowBoxes().withIndex()
                .filter { (_, box) -> box.isExpanded }
                .map { v -> v.index }
        }
    }

    private fun allFlowBoxes() = listView.getBoxes().flatMap { box ->
        val groupPane = box.content as? FlowGroupPane ?: return@flatMap emptyList()
        groupPane.flowsView.getBoxes()
    }

    private class FlowNamePrompt(
        private val takenFlowNames: Set<String>,
        title: String, initialText: String,
    ) : TextPrompt<String>(title, initialText) {
        override fun convert(text: String): String? = text.takeIf { Identifier.isValid(it) && it !in takenFlowNames }
    }

    companion object : Type(13, "Flows") {
        override val icon: Ikon get() = MaterialDesignT.TUNE

        override val defaultSide: Side
            get() = Side.BOTTOM

        override fun createToolPane(project: PonticelloProject): ToolPane = AudioFlowPane(project.flows)

        val actions = collectActions<AudioFlowGroup> {
            addAction("Add flow") {
                icon(MaterialDesignP.PLUS)
                executes { group, ev ->
                    val options = FlowOption.getOptions(group.context)
                    val option = SimpleSearchableListView(options, "Add flow").showPopup(ev) ?: return@executes
                    val defaultName = option.defaultName()
                    val takenFlowNames = group.context[AudioFlows].allFlows().mapTo(mutableSetOf()) { f -> f.name.now }
                    val idx = (1..Int.MAX_VALUE).first { idx -> "${defaultName}_$idx" !in takenFlowNames }
                    val name = FlowNamePrompt(takenFlowNames, "Flow name", "${defaultName}_$idx")
                        .showDialog(ev) ?: return@executes
                    val anchor = ev.popupAnchor()
                    val flow = option.createFlow(group.context, anchor) ?: return@executes
                    flow.setInitialName(name)
                    group.flows.add(flow)
                }
            }
            addAction("Toggle activated") {
                description { grp ->
                    `if`(
                        grp.isActive,
                        then = { "Deactivate group" },
                        otherwise = { "Activate group" }
                    )
                }
                icon { grp ->
                    grp.isActive.map { active ->
                        if (active) MaterialDesignR.RADIOBOX_MARKED
                        else MaterialDesignR.RADIOBOX_BLANK
                    }
                }
                executes { grp -> grp.toggleActive() }
            }
        }
    }
}