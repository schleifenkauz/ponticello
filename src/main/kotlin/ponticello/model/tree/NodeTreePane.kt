package ponticello.model.tree

import fxutils.actions.ContextualizedAction
import fxutils.actions.collectActions
import fxutils.button
import fxutils.hspace
import fxutils.label
import fxutils.setFixedWidth
import javafx.scene.Node
import org.kordamp.ikonli.Ikon
import org.kordamp.ikonli.materialdesign2.MaterialDesignE
import org.kordamp.ikonli.materialdesign2.MaterialDesignF
import ponticello.model.instr.InstrumentReference
import ponticello.model.project.PonticelloProject
import ponticello.ui.dock.*
import ponticello.ui.flow.TabbedAudioFlowsPane
import ponticello.ui.registry.InstrumentRegistryPane
import ponticello.ui.registry.ObjectBox
import ponticello.ui.registry.ObjectListView
import ponticello.ui.score.ScoreObjectViewPane
import reaktive.value.binding.flatMap
import reaktive.value.fx.asObservableValue

class NodeTreePane(list: AudioNodeTree) : ListToolPane<AudioNode>(list) {
    override val type: Type
        get() = NodeTreePane

    override val canCreateNewObject: Boolean
        get() = false

    override fun canDelete(obj: AudioNode): Boolean = false

    override fun defaultState(): ToolPaneState = ToolPaneState.window

    override val headerActions: List<ContextualizedAction> get() = emptyList()

    override fun getHeaderContent(obj: AudioNode): List<Node> = buildList {
        val nameLabel = label(obj.name).setFixedWidth(150.0)
        nameLabel.textFillProperty().bind(obj.associatedColor.asObservableValue())
        add(nameLabel)
        if (obj is AudioNode.SoundProcessInstance) {
            add(hspace(5.0))
            val instrumentName = obj.process.instrumentRef.flatMap(InstrumentReference::name)
            add(button(instrumentName) {
                val pane = obj.context[AppLayout].get<InstrumentRegistryPane>()
                pane.showContent(obj.process.def)
            })
        }
    }

    override fun getActions(box: ObjectBox<AudioNode>): List<ContextualizedAction> = actions.withContext(box.obj)

    override fun configureBox(
        box: ObjectBox<AudioNode>,
        currentMode: ObjectListView.DisplayMode
    ) {
        box.setOnMouseClicked { ev ->
            if (ev.clickCount == 2) {
                viewObject(box.obj)
            }
        }
    }

    companion object : Type(uid = 21, "Node Tree") {
        override val defaultSide: Side
            get() = Side.RIGHT

        override val icon: Ikon
            get() = MaterialDesignF.FILE_TREE_OUTLINE

        override val shortcut: String
            get() = "Ctrl+Alt+T"

        override fun createToolPane(project: PonticelloProject): ToolPane = NodeTreePane(project.context[AudioNodeTree])

        private val actions = collectActions<AudioNode> {
            addAction("View") {
                icon(MaterialDesignE.EYE)
                executes { node -> viewObject(node) }
            }
        }

        private fun viewObject(obj: AudioNode) {
            when (obj) {
                is AudioNode.FlowGroup -> {
                    val flowsPane = obj.context[AppLayout].get<TabbedAudioFlowsPane>()
                    flowsPane.setShowing(true)
                    flowsPane.select(obj.group)
                }

                is AudioNode.SoundProcessInstance -> {
                    val viewPane = obj.context[AppLayout].get<ScoreObjectViewPane>()
                    viewPane.showContent(obj.process) //TODO select ScoreObjectView by absolute position
                }
            }
        }
    }
}

