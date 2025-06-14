package ponticello.ui.flow

import fxutils.actions.*
import fxutils.alwaysVGrow
import fxutils.centerChildren
import fxutils.setFixedWidth
import fxutils.vspace
import javafx.geometry.Orientation
import javafx.scene.Node
import javafx.scene.Parent
import javafx.scene.layout.Region
import javafx.scene.layout.VBox
import javafx.scene.paint.Color
import javafx.scene.text.Text
import org.kordamp.ikonli.Ikon
import org.kordamp.ikonli.materialdesign2.MaterialDesignC
import org.kordamp.ikonli.materialdesign2.MaterialDesignT
import ponticello.model.flow.AudioFlowGroup
import ponticello.model.flow.AudioFlows
import ponticello.model.project.PonticelloProject
import ponticello.model.project.flows
import ponticello.ui.dock.*
import ponticello.ui.impl.colorPicker
import ponticello.ui.registry.ObjectBox
import ponticello.ui.registry.ObjectListView.DisplayMode
import reaktive.value.binding.not
import reaktive.value.fx.asObservableValue

class AudioFlowsPane(flows: AudioFlows) : SearchableToolPane<AudioFlowGroup>(flows) {
    override val type: Type
        get() = AudioFlowsPane

    override val inlineOrientation: Orientation
        get() = Orientation.HORIZONTAL

    override val supportedModes: Set<DisplayMode>
        get() = setOf(DisplayMode.Collapsable, DisplayMode.SubWindow, DisplayMode.DetailsPane)

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

    override fun getHeaderContent(obj: AudioFlowGroup): List<Node> {
        val colorPicker = colorPicker(obj.associatedColor).setFixedWidth(30.0)
        return listOf(colorPicker)
    }

    override fun getActions(box: ObjectBox<AudioFlowGroup>): List<ContextualizedAction> =
        groupActions.withContext(box)

    override fun getContent(obj: AudioFlowGroup, mode: DisplayMode): Parent =
        FlowGroupPane(obj, ownWindow = mode == DisplayMode.SubWindow)

    override fun collapsedLayout(box: ObjectBox<AudioFlowGroup>, header: Region, content: Parent?): Node {
        val nameLabel = Text()
        nameLabel.textProperty().bind(box.obj.name.asObservableValue())
        nameLabel.fill = Color.WHITE
        nameLabel.rotate = -90.0
        val expandAction = MaterialDesignC.CHEVRON_RIGHT.button("Expand", "medium-icon-button") { _ ->
            box.toggleExpanded()
        }
        val toggleActive = FlowGroupPane.toggleActiveAction.withContext(box.obj).makeButton("medium-icon-button")
        return VBox(expandAction, toggleActive, vspace(20.0), nameLabel)
            .alwaysVGrow().centerChildren().setFixedWidth(30.0)
    }

    override fun onDeselected(obj: AudioFlowGroup) {
        val groupPane = listView.getBox(obj).content as? FlowGroupPane ?: return
        groupPane.flowsView.deselectAll()
    }

    override fun saveState(dest: ToolPaneState) {
        super.saveState(dest)
        if (dest is FlowPaneState && isSetup) {
            dest.expandedFlows = allFlowBoxes().withIndex()
                .filter { (_, box) -> box.isExpanded }
                .map { v -> v.index }
        }
    }

    private fun allFlowBoxes() = listView.getBoxes().flatMap { box ->
        val groupPane = box.content as? FlowGroupPane ?: return@flatMap emptyList()
        groupPane.flowsView.getBoxes()
    }

    companion object : Type(13, "Flows") {
        override val icon: Ikon get() = MaterialDesignT.TUNE

        override val defaultSide: Side
            get() = Side.BOTTOM

        override val shortcuts: Array<String> get() = arrayOf("F9")

        override fun createToolPane(project: PonticelloProject): ToolPane = AudioFlowsPane(project.flows)

        private val groupActions = collectActions<ObjectBox<AudioFlowGroup>> {
            add(FlowGroupPane.toggleActiveAction) { box -> box.obj }
            add(FlowGroupPane.addFlowAction.map { box: ObjectBox<AudioFlowGroup> -> box.obj }) {
                enableWhen { box -> box.isCollapsed.not() }
                ifNotApplicable(Action.IfNotApplicable.Hide)
            }
        }
    }
}