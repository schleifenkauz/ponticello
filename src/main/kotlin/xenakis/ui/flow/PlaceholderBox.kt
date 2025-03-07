package xenakis.ui.flow

import fxutils.label
import javafx.scene.Node
import javafx.scene.control.Label
import xenakis.model.flow.ScoreObjectPlaceholder

class PlaceholderBox(placeholder: ScoreObjectPlaceholder) : FlowBox<ScoreObjectPlaceholder>(placeholder) {
    override fun getContent(flow: ScoreObjectPlaceholder): Node = label(flow.group.name)

    override fun getTitle(flow: ScoreObjectPlaceholder): Node = Label("Placeholder")
}