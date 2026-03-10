package ponticello.ui.flow

import fxutils.actions.ContextualizedAction
import fxutils.actions.button
import fxutils.actions.collectActions
import fxutils.actions.makeButton
import fxutils.centerChildren
import fxutils.makeVerticalLabel
import fxutils.prompt.SelectorPrompt
import fxutils.setFixedWidth
import fxutils.vspace
import hextant.context.Context
import javafx.geometry.Orientation
import javafx.scene.Node
import javafx.scene.Parent
import javafx.scene.layout.Priority
import javafx.scene.layout.VBox
import javafx.scene.text.Text
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.kordamp.ikonli.Ikon
import org.kordamp.ikonli.materialdesign2.MaterialDesignC
import org.kordamp.ikonli.materialdesign2.MaterialDesignT
import ponticello.impl.Logger
import ponticello.model.flow.AudioFlow
import ponticello.model.flow.AudioFlowGroup
import ponticello.model.flow.AudioFlows
import ponticello.model.instr.BusObject
import ponticello.model.project.PonticelloProject
import ponticello.model.project.flows
import ponticello.model.server.BusRegistry
import ponticello.ui.dock.*
import ponticello.ui.impl.colorPicker
import ponticello.ui.registry.ObjectBox
import ponticello.ui.registry.ObjectListView.DisplayMode
import reaktive.value.fx.asObservableValue
import reaktive.value.now

class AudioFlowsPane(flows: AudioFlows) : SearchableToolPane<AudioFlowGroup>(flows) {
    override val type: Type
        get() = AudioFlowsPane

    override val inlineOrientation: Orientation
        get() = Orientation.HORIZONTAL

    override val supportedModes: Set<DisplayMode>
        get() = setOf(DisplayMode.Collapsable, DisplayMode.SubWindow, DisplayMode.DetailsPane)

    override val canCreateNewObject: Boolean
        get() = false

    override val nameDisplayWidth: Double
        get() = 100.0

    override val canDuplicate: Boolean
        get() = true

    private var selectedFilter: FilterOption = FilterOption.All
        set(value) {
            if (field == value) return
            field = value
            refilterFlows()
        }

    private val filterSelector by lazy {
        SearchableFilterOptionPopup(context)
            .selectorButton(this::selectedFilter)
            .setFixedWidth(150.0)
    }

    override val headerContent: Node
        get() = filterSelector

    init {
        styleClass.add("flow-pane")
    }

    override fun defaultState(): ToolPaneState = FlowPaneState.default()

    override fun afterSetup() {
        super.afterSetup()
        listView.autoResizeScene = true
        refilterFlows()
        val state = initialState
        if (state is FlowPaneState) {
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

    private fun refilterFlows() {
        for (box in listView.getBoxes()) {
            val content = box.content as? FlowGroupPane ?: continue
            content.flowsView.refilter()
        }
    }

    override fun getHeaderContent(obj: AudioFlowGroup): List<Node> {
        val colorPicker = colorPicker(obj.associatedColor).setFixedWidth(30.0)
        return listOf(colorPicker)
    }

    override fun getActions(box: ObjectBox<AudioFlowGroup>): List<ContextualizedAction> =
        groupActions.withContext(box)

    override fun getContent(obj: AudioFlowGroup, box: ObjectBox<AudioFlowGroup>): Parent =
        FlowGroupPane(obj, parent = this)

    fun filter(flow: AudioFlow) = when (val filter = selectedFilter) {
        FilterOption.All -> true
        FilterOption.Active -> flow.isActive.now
        is FilterOption.Bus -> flow.usesBus(filter.bus)
    }

    override fun collapsedLayout(box: ObjectBox<AudioFlowGroup>): Node {
        val nameLabel = Text()
        nameLabel.textProperty().bind(box.obj.name.asObservableValue())
        val namePane = makeVerticalLabel(nameLabel).setFixedWidth(15.0)
        setVgrow(namePane, Priority.ALWAYS)

        val expandAction = MaterialDesignC.CHEVRON_RIGHT.button("Expand", "medium-icon-button") { _ ->
            box.toggleExpanded()
        }
        val toggleActive = FlowGroupPane.toggleActiveAction.withContext(box.obj).makeButton("medium-icon-button")
        return VBox(expandAction, toggleActive, vspace(20.0), namePane)
            .centerChildren().setFixedWidth(30.0)
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

    @Serializable
    private sealed interface FilterOption {
        @Serializable
        @SerialName("all")
        data object All : FilterOption

        @Serializable
        @SerialName("active")
        data object Active : FilterOption

        @Serializable
        @SerialName("bus")
        data class Bus(val bus: BusObject) : FilterOption
    }

    private class SearchableFilterOptionPopup(
        private val context: Context,
    ) : SelectorPrompt<FilterOption>("Select filter") {
        override fun options(): List<FilterOption> =
            listOf(FilterOption.All, FilterOption.Active) + context[BusRegistry].map { FilterOption.Bus(it) }

        override fun extractText(option: FilterOption): String = when (option) {
            FilterOption.All -> "All"
            FilterOption.Active -> "Active"
            is FilterOption.Bus -> option.bus.name.now
        }
    }

    companion object : Type(uid = 13, "Flows") {
        override val icon: Ikon get() = MaterialDesignT.TUNE

        override val defaultSide: Side
            get() = Side.BOTTOM

        override val shortcut: String get() = "F9"

        override fun createToolPane(project: PonticelloProject): ToolPane = AudioFlowsPane(project.flows)

        private val groupActions = collectActions<ObjectBox<AudioFlowGroup>> {
            add(FlowGroupPane.toggleActiveAction) { box -> box.obj }
        }
    }
}