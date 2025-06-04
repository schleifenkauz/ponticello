package ponticello.ui.flow

import fxutils.actions.collectActions
import fxutils.popupAnchor
import fxutils.prompt.SimpleSearchableListView
import fxutils.prompt.TextPrompt
import fxutils.setFixedWidth
import fxutils.setupDropArea
import javafx.geometry.Orientation
import javafx.scene.Node
import javafx.scene.Parent
import org.kordamp.ikonli.materialdesign2.MaterialDesignP
import org.kordamp.ikonli.materialdesign2.MaterialDesignR
import ponticello.model.flow.AudioFlowGroup
import ponticello.model.flow.AudioFlows
import ponticello.sc.Identifier
import ponticello.ui.impl.colorPicker
import ponticello.ui.registry.ObjectListView
import ponticello.ui.registry.ObjectListView.DisplayMode
import ponticello.ui.registry.SearchableToolPane
import reaktive.value.binding.`if`
import reaktive.value.binding.map
import reaktive.value.now

class AudioFlowPane(flows: AudioFlows) : SearchableToolPane<AudioFlowGroup>() {
    override val inlineOrientation: Orientation
        get() = Orientation.HORIZONTAL

    override val supportedModes: Set<DisplayMode>
        get() = setOf(DisplayMode.Inline, DisplayMode.SubWindow, DisplayMode.DetailsPane)

    init {
        styleClass.add("flow-pane")
        setup(title = null, flows)
        listView.itemsScrollPane.isFitToHeight = true
        listView.autoResizeScene = true
    }

    override fun getItemContent(obj: AudioFlowGroup): List<Node> {
        val colorPicker = colorPicker(obj.associatedColor).setFixedWidth(30.0)
        return listOf(colorPicker)
    }

    override fun getContent(obj: AudioFlowGroup, mode: DisplayMode): Parent {
        val config = FlowListConfig(obj, autoResizeScene = mode == DisplayMode.SubWindow)
        val listView = ObjectListView(obj.flows, config)
        listView.setupDropArea(config::canDrop) { ev -> config.onDrop(ev, listView) }
        return listView
    }

    private class FlowNamePrompt(
        private val takenFlowNames: Set<String>,
        title: String, initialText: String,
    ) : TextPrompt<String>(title, initialText) {
        override fun convert(text: String): String? = text.takeIf { Identifier.isValid(it) && it !in takenFlowNames }
    }

    companion object {
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