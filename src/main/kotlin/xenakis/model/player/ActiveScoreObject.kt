package xenakis.model.player

import reaktive.value.now
import xenakis.model.score.ObjectPosition
import xenakis.model.score.ScoreObject

class ActiveScoreObject(
    val obj: ScoreObject,
    val absolutePosition: ObjectPosition,
    val suffix: Int,
) : ActiveObject() {
    override val associatedObject: ScoreObject
        get() = obj

    override val uniqueName: String
        get() = ActiveObjectManager.uniqueName(obj.name.now, suffix)

    override val superColliderName: String
        get() = obj.superColliderName(suffix)
}