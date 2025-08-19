package ponticello.model.player

import ponticello.model.score.ObjectPosition
import ponticello.model.score.ScoreObjectInstance
import reaktive.value.now

data class ScoreEvent(
    val type: Type,
    val absolutePosition: ObjectPosition,
    val inst: ScoreObjectInstance,
) {
    override fun toString(): String = "$type: ${inst.obj.name.now} at $absolutePosition"

    enum class Type {
        Dummy, ObjectStart, ObjectEnd;
    }
}
