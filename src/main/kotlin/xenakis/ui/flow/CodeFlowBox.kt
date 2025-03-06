package xenakis.ui.flow

import javafx.scene.Node
import reaktive.value.ReactiveString
import reaktive.value.reactiveValue
import xenakis.model.flow.CodeFlow

class CodeFlowBox(flow: CodeFlow) : FlowBox<CodeFlow>(flow) {
    override fun getContent(flow: CodeFlow): Node = flow.codeEditor.control

    override fun getTitle(flow: CodeFlow): ReactiveString = reactiveValue("Code")
}