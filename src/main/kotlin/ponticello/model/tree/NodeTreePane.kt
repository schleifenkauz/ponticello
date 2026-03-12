package ponticello.model.tree

import fxutils.actions.ContextualizedAction
import fxutils.actions.collectActions
import fxutils.label
import javafx.scene.Node
import org.kordamp.ikonli.Ikon
import org.kordamp.ikonli.materialdesign2.MaterialDesignE
import org.kordamp.ikonli.materialdesign2.MaterialDesignF
import ponticello.model.project.PonticelloProject
import ponticello.ui.dock.*
import ponticello.ui.flow.TabbedAudioFlowsPane
import ponticello.ui.registry.ObjectBox
import ponticello.ui.registry.ObjectListView
import ponticello.ui.score.ScoreObjectViewPane

class NodeTreePane(list: AudioNodeTree) : ListToolPane<AudioNode>(list) {
    override val type: Type
        get() = NodeTreePane

    override val canCreateNewObject: Boolean
        get() = false

    override fun canDelete(obj: AudioNode): Boolean = false

    override fun defaultState(): ToolPaneState = ToolPaneState.window

    override val headerActions: List<ContextualizedAction> get() = emptyList()

    override fun getHeaderContent(obj: AudioNode): List<Node> {
        val name = label(obj.name)
        return listOf(name)
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
                    viewPane.showContent(obj.process) //TODO get ScoreObjectView by absolute position
                }
            }
        }
    }
}

