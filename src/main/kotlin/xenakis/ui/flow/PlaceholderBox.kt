package xenakis.ui.flow

import fxutils.label
import javafx.scene.Node
import xenakis.model.flow.ScoreObjectPlaceholder
import xenakis.model.obj.GroupObject

class PlaceholderBox(placeholder: ScoreObjectPlaceholder) : FlowBox<ScoreObjectPlaceholder>(placeholder) {
    override fun getContent(): Node = label(flow.group.get<GroupObject>().name)
}