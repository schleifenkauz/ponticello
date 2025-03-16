package xenakis.ui.flow

import fxutils.label
import javafx.scene.Node
import javafx.scene.control.Label
import xenakis.model.flow.ScoreObjectPlaceholder
import xenakis.model.obj.GroupObject

class PlaceholderBox(placeholder: ScoreObjectPlaceholder) : FlowBox<ScoreObjectPlaceholder>(placeholder) {
    override fun getHeader(): Node = Label("Group")

    override fun getContent(): Node = label(flow.groupRef.name)
}