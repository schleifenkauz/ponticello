package xenakis.model.player

import reaktive.value.now
import xenakis.model.score.ObjectPosition
import xenakis.model.score.ScoreObject

data class ActiveScoreObject(
    val player: ScorePlayer,
    val obj: ScoreObject,
    val absolutePosition: ObjectPosition,
    val suffix: Int,
) : ActiveObject() {
    override val associatedObject: ScoreObject
        get() = obj

    override val uniqueName: String
        get() = ActiveObjectsManager.uniqueName(obj.name.now, suffix)

    override val superColliderName: String
        get() = obj.superColliderPrefix + uniqueName

    override fun toString(): String = "$obj [$suffix] at $absolutePosition"
}