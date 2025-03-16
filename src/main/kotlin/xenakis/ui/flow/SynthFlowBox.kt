package xenakis.ui.flow

import bundles.createBundle
import fxutils.actions.Action
import fxutils.actions.ContextualizedAction
import fxutils.actions.collectActions
import fxutils.actions.isShiftDown
import fxutils.centerChildren
import javafx.scene.Node
import javafx.scene.layout.HBox
import org.kordamp.ikonli.material2.Material2AL
import org.kordamp.ikonli.material2.Material2MZ
import xenakis.model.flow.SynthFlow
import xenakis.sc.view.ObjectSelectorControl
import xenakis.ui.controls.ControlAssignmentView
import xenakis.ui.launcher.XenakisMainActivity
import xenakis.ui.score.ParameterizedScoreObjectView

class SynthFlowBox(flow: SynthFlow) : FlowBox<SynthFlow>(flow) {
    override fun getContent(): Node {
        return ControlAssignmentView(flow)
    }

    override fun getHeader(): Node = HBox(
        5.0,
        makeNameLabel(flow),
        ObjectSelectorControl(flow.synthDefSelector, createBundle())
    ).centerChildren()

    override val extraActions: List<ContextualizedAction> = actions.withContext(this)

    companion object {
        private val actions = collectActions<SynthFlowBox> {
            addAction("View SynthDef") {
                icon(Material2AL.CODE)
                shortcut("Ctrl+L")
                applicableIf { box -> box.flow.synthDefSelector.isResolved }
                ifNotApplicable(Action.IfNotApplicable.Disable)
                executes { box ->
                    val instrumentsPane = box.flow.context[XenakisMainActivity].instrumentsPane

                    instrumentsPane.editInstrument(box.flow.synthDef!!)
                }
            }
            addAction("Add parameter") {
                icon(Material2MZ.PLUS)
                shortcut("Ctrl+INSERT")
                executes { box, ev ->
                    if (ev.isShiftDown()) {
                        box.flow.addControlsForAllObjectParameters()
                    } else {
                        ParameterizedScoreObjectView.addNewControl(box.flow, anchorNode = box.header)
                    }
                }
            }
        }
    }
}