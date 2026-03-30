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
import ponticello.model.obj.InstrumentReference
import ponticello.model.project.PonticelloProject
import ponticello.sc.client.SuperColliderClient
import ponticello.ui.dock.*
import ponticello.ui.flow.TabbedAudioFlowsPane
import ponticello.ui.registry.InstrumentRegistryPane
import ponticello.ui.registry.ObjectBox
import ponticello.ui.registry.ObjectListView
import ponticello.ui.score.ScoreObjectViewPane
import reaktive.value.binding.flatMap
import reaktive.value.fx.asObservableValue

class NodeTreePane(private val tree: AudioNodeTree) : ListToolPane<AudioNode>(tree) {
    override val type: Type
        get() = NodeTreePane

    override val canCreateNewObject: Boolean
        get() = false

    override fun canDelete(obj: AudioNode): Boolean = false

    override fun defaultState(): ToolPaneState = ToolPaneState.window

    override val headerActions: List<ContextualizedAction> get() = paneActions.withContext(tree)

    override fun getHeaderContent(obj: AudioNode): List<Node> = buildList {
        val nameLabel = label(obj.name).setFixedWidth(150.0)
        nameLabel.textFillProperty().bind(obj.associatedColor.asObservableValue())
        add(nameLabel)
        if (obj is AudioNode.SoundProcessInstance) {
            add(hspace(5.0))
            val instrumentName = obj.process.instrumentRef.flatMap(InstrumentReference::name)
            add(button(instrumentName) {
                val pane = obj.context[AppLayout].get<InstrumentRegistryPane>()
                pane.showContent(obj.process.getInstrument())
            })
        }
    }

    override fun getActions(box: ObjectBox<AudioNode>): List<ContextualizedAction> = itemActions.withContext(box.obj)

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

        private val paneActions = collectActions<AudioNodeTree> {
            addAction("Show Server Tree") {
                icon(MaterialDesignF.FILE_TREE)
                executes { pane ->
                    val client = pane.context[SuperColliderClient]
                    client.run("AppClock.sched(0) { s.plotTree }")
                }
            }
        }

        private val itemActions = collectActions<AudioNode> {
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

