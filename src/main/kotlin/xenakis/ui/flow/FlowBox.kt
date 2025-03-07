package xenakis.ui.flow

import fxutils.actions.*
import fxutils.infiniteSpace
import fxutils.styleClass
import javafx.scene.Node
import javafx.scene.input.TransferMode
import javafx.scene.layout.HBox
import javafx.scene.layout.VBox
import org.kordamp.ikonli.material2.Material2AL
import org.kordamp.ikonli.materialdesign2.MaterialDesignC
import org.kordamp.ikonli.materialdesign2.MaterialDesignR
import reaktive.value.binding.map
import reaktive.value.binding.notEqualTo
import reaktive.value.now
import reaktive.value.reactiveValue
import xenakis.model.flow.AudioFlow
import xenakis.ui.launcher.XenakisLauncher.Companion.currentProject

abstract class FlowBox<F : AudioFlow>(val flow: F) : VBox() {
    protected open val extraActions: List<ContextualizedAction>
        get() = emptyList()

    lateinit var header: HBox
        private set

    protected abstract fun getContent(flow: F): Node

    protected abstract fun getTitle(flow: F): Node

    init {
        styleClass.add("flow-box")
        setOnMouseClicked { requestFocus() }
        registerShortcuts(actions.withContext(flow))
    }

    fun initialize() {
        val actions = extraActions + FlowBox.actions.withContext(flow)
        val actionBar = ActionBar(actions, buttonStyle = "flow-action-button")
        header = HBox(
            getTitle(flow),
            infiniteSpace(),
            actionBar
        ) styleClass "flow-box-header"
        header.setOnDragDetected { ev ->
            val mode = if (ev.isControlDown) TransferMode.COPY else TransferMode.MOVE
            val db = startDragAndDrop(mode)
            val referenceIndex = flow.context[currentProject].flows.referenceIndex(flow)
            db.setContent(mapOf(AudioFlow.DATA_FORMAT to referenceIndex))
            ev.consume()
        }
        children.addAll(header, getContent(flow))
    }

    companion object {
        private val actions = collectActions<AudioFlow> {
            addAction("Move up") {
                applicableIf { flow -> flow.index.notEqualTo(0) }
                ifNotApplicable(Action.IfNotApplicable.Disable)
                shortcut("Ctrl+UP")
                icon(Material2AL.ARROW_UPWARD)
                executes { flow ->
                    val flows = flow.context[currentProject].flows
                    flows.moveFlow(flow, flow.index.now - 1)
                }
            }
            addAction("Move down") {
                applicableIf { flow ->
                    val flows = flow.context[currentProject].flows
                    val lastFlowIndex = flows.numberOfFlows(flow.associatedBus).map { s -> s - 1 }
                    flow.index.notEqualTo(lastFlowIndex)
                }
                ifNotApplicable(Action.IfNotApplicable.Disable)
                shortcut("Ctrl+DOWN")
                icon(Material2AL.ARROW_DOWNWARD)
                executes { flow ->
                    val flows = flow.context[currentProject]
                    flows.context[currentProject].flows.moveFlow(flow, flow.index.now + 1)
                }
            }
            addAction("Toggle activated") {
                icon { flow ->
                    flow.isActive.map { active ->
                        if (active) MaterialDesignR.RADIOBOX_MARKED
                        else MaterialDesignR.RADIOBOX_BLANK
                    }
                }
                applicableIf { flow -> reactiveValue(flow.canDeactivate) }
                shortcut("Ctrl+T")
                executes { flow -> flow.isActive.now = !flow.isActive.now }
            }
            addAction("Remove flow") {
                icon(MaterialDesignC.CLOSE_BOX)
                shortcuts("Ctrl+DELETE")
                executes { flow ->
                    val flows = flow.context[currentProject].flows
                    flows.removeFlow(flow)
                }
            }
        }
    }
}