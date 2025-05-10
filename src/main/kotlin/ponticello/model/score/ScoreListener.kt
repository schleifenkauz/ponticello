package ponticello.model.score

import ponticello.impl.Decimal

interface ScoreListener {
    fun addedObject(score: Score, inst: ScoreObjectInstance, autoSelect: Boolean)

    fun removedObject(score: Score, inst: ScoreObjectInstance)

    fun movedObject(score: Score, inst: ScoreObjectInstance, dt: Decimal, dy: Decimal) {}

    fun toggledMute(score: Score, inst: ScoreObjectInstance, muted: Boolean) {}
}