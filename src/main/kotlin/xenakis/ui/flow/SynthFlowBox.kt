package xenakis.ui.flow

import fxutils.prompt.DetailPane
import javafx.scene.Node
import reaktive.value.ReactiveString
import xenakis.model.flow.SynthFlow
import xenakis.ui.score.ParameterizedScoreObjectView

class SynthFlowBox(flow: SynthFlow) : FlowBox<SynthFlow>(flow) {
    override fun getContent(flow: SynthFlow): Node {
        val detailPane = DetailPane()
        ParameterizedScoreObjectView.setupSynthDetailPane(detailPane, flow)
        return detailPane
    }

    override fun getTitle(flow: SynthFlow): ReactiveString = flow.synthDef.name
}