package xenakis.ui.flow

import fxutils.infiniteSpace
import fxutils.styleClass
import javafx.scene.Node
import javafx.scene.control.Button
import javafx.scene.input.TransferMode
import javafx.scene.layout.HBox
import javafx.scene.layout.VBox
import org.kordamp.ikonli.material2.Material2AL
import org.kordamp.ikonli.materialdesign2.MaterialDesignC
import org.kordamp.ikonli.materialdesign2.MaterialDesignR
import reaktive.value.ReactiveString
import reaktive.value.binding.map
import reaktive.value.binding.notEqualTo
import reaktive.value.now
import xenakis.model.flow.AudioFlow
import xenakis.ui.actions.Action
import xenakis.ui.actions.ActionBar
import xenakis.ui.actions.collectActions
import xenakis.ui.actions.registerShortcuts
import xenakis.ui.impl.label
import xenakis.ui.impl.setupDragging
import xenakis.ui.launcher.XenakisLauncher.Companion.currentProject

abstract class FlowBox<F : AudioFlow>(val flow: F) : VBox() {
    abstract fun getContent(flow: F): Node

    abstract fun getTitle(flow: F): ReactiveString

    init {
        styleClass.add("flow-box")
        setOnMouseClicked { requestFocus() }
        registerShortcuts(actions.withContext(flow))
    }

    fun initialize(parent: VerticalFlowsBox) {
        val actionBar = ActionBar(actions.withContext(flow), border = false, buttonStyle = "flow-action-button")
        val header = HBox(
            label(getTitle(flow)),
            infiniteSpace(),
            actionBar
        ) styleClass "flow-box-header"
        val grabber = actionBar.getButton(actions.getAction("Drag flow"))
        //setupVerticalDragging(grabber, parent)
        grabber.setOnDragDetected { ev ->
            val mode = if (ev.isControlDown) TransferMode.COPY else TransferMode.MOVE
            val db = startDragAndDrop(mode)
            val referenceIndex = flow.context[currentProject].flows.referenceIndex(flow)
            db.setContent(mapOf(AudioFlow.DATA_FORMAT to referenceIndex))
            ev.consume()
        }
        children.addAll(header, getContent(flow))
    }

    private fun setupVerticalDragging(grabber: Button, parent: VerticalFlowsBox) {
        grabber.setupDragging(flow.context, null,
            onPressed = { viewOrder = -100.0 },
            relocateBy = { _, _, _, _, dy -> translateY = dy },
            onReleased = {
                viewOrder = 0.0
                try {
                    val y = translateY + layoutY
                    var newIndex = parent.vbox.children.binarySearchBy(y) { n -> n.layoutY }
                    if (newIndex < 0) newIndex = -(newIndex + 1)
                    flow.context[currentProject].flows.moveFlow(flow, newIndex)
                } finally {
                    translateY = 0.0
                }
            }
        )
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
            addAction("Drag flow") {
                icon(MaterialDesignC.CURSOR_POINTER)
            }
        }
    }
}