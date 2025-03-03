package xenakis.model.flow

import xenakis.model.score.ObjectPosition

data class ScoreObjectInfo(
    val absolutePosition: ObjectPosition,
    val superColliderName: String,
    val placement: NodePlacement?
)