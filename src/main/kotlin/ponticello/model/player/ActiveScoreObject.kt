package ponticello.model.player

import reaktive.value.now
import ponticello.model.obj.ParameterDefObject
import ponticello.model.score.ObjectPosition
import ponticello.model.score.ScoreObject
import ponticello.model.score.controls.ParameterControl

data class ActiveScoreObject(
    val player: ScorePlayer,
    val obj: ScoreObject,
    val absolutePosition: ObjectPosition,
    val suffix: Int,
    val extraArguments: Map<ParameterDefObject, ParameterControl>
) : ActiveObject() {
    private var stillActive = true

    val isStillActive: Boolean
        get() = stillActive && absolutePosition.time + obj.duration > player.currentTime

    fun stopped() {
        stillActive = false
    }

    override val associatedObject: ScoreObject
        get() = obj

    override val uniqueName: String
        get() = ActiveObjectsManager.uniqueName(obj.name.now, suffix)

    override val superColliderName: String
        get() = obj.superColliderPrefix + uniqueName

    override fun toString(): String = "$obj [$suffix] at $absolutePosition"
}