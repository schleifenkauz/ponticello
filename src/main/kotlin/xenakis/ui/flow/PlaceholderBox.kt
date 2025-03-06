package xenakis.ui.flow

import javafx.scene.Node
import reaktive.value.ReactiveString
import reaktive.value.reactiveValue
import xenakis.model.flow.ScoreObjectPlaceholder
import xenakis.ui.impl.label

class PlaceholderBox(placeholder: ScoreObjectPlaceholder) : FlowBox<ScoreObjectPlaceholder>(placeholder) {
    override fun getContent(flow: ScoreObjectPlaceholder): Node = label(flow.group.name)

    override fun getTitle(flow: ScoreObjectPlaceholder): ReactiveString = reactiveValue("Placeholder")
}