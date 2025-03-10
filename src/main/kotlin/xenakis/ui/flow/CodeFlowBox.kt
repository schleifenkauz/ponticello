package xenakis.ui.flow

import javafx.scene.Node
import xenakis.model.flow.CodeFlow

class CodeFlowBox(flow: CodeFlow) : FlowBox<CodeFlow>(flow) {
    override fun getContent(): Node = flow.codeEditor.control
}