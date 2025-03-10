package xenakis.ui.flow

import fxutils.actions.*
import fxutils.infiniteSpace
import fxutils.label
import fxutils.styleClass
import javafx.scene.Node
import javafx.scene.input.TransferMode
import javafx.scene.layout.HBox
import javafx.scene.layout.VBox
import org.kordamp.ikonli.material2.Material2AL
import org.kordamp.ikonli.materialdesign2.MaterialDesignC
import org.kordamp.ikonli.materialdesign2.MaterialDesignR
import reaktive.value.binding.map
import reaktive.value.binding.not
import reaktive.value.now
import reaktive.value.reactiveValue
import xenakis.model.flow.AudioFlow
import xenakis.ui.controls.RenamePrompt
import xenakis.ui.launcher.XenakisLauncher.Companion.currentProject

abstract class FlowBox<F : AudioFlow>(val flow: F) : VBox() {
    protected open val extraActions: List<ContextualizedAction>
        get() = emptyList()

    lateinit var header: HBox
        private set

    protected abstract fun getContent(): Node

    protected open fun getHeader(): Node = makeNameLabel(flow)

    protected fun makeNameLabel(flow: F) =
        label(flow.name.map { n -> if (n == AudioFlow.NO_NAME) flow.getDefaultName() else n })

    init {
        styleClass.add("flow-box")
        setOnMouseClicked { requestFocus() }
        registerShortcuts(actions.withContext(flow))
    }

    fun initialize() {
        val actions = extraActions + FlowBox.actions.withContext(flow)
        val actionBar = ActionBar(actions, buttonStyle = "flow-action-button")
        header = HBox(
            getHeader(),
            renameAction.withContext(flow).makeButton("flow-action-button"),
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
        children.addAll(header, getContent())
    }

    companion object {
        private val renameAction = action<AudioFlow>("Rename") {
            icon(Material2AL.EDIT)
            shortcuts("F2")
            executes { flow, ev ->
                val name = flow.name.now
                val defaultName = if (name == AudioFlow.NO_NAME) flow.getDefaultName() else name
                RenamePrompt(flow, "Rename flow", defaultName).showDialog(ev)
            }
        }

        private val actions = collectActions<AudioFlow> {
            addAction("Move up") {
                applicableIf { flow -> flow.isFirst.not() }
                ifNotApplicable(Action.IfNotApplicable.Disable)
                shortcut("Ctrl+UP")
                icon(Material2AL.ARROW_UPWARD)
                executes { flow ->
                    val flows = flow.context[currentProject].flows
                    val index = flows.indexOf(flow)
                    flows.moveFlow(flow, index - 1)
                }
            }
            addAction("Move down") {
                applicableIf { flow -> flow.isLast.not() }
                ifNotApplicable(Action.IfNotApplicable.Disable)
                shortcut("Ctrl+DOWN")
                icon(Material2AL.ARROW_DOWNWARD)
                executes { flow ->
                    val flows = flow.context[currentProject].flows
                    val index = flows.indexOf(flow)
                    flows.moveFlow(flow, index + 1)
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