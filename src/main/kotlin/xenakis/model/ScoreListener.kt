package xenakis.model

interface ScoreListener {
    fun addedObject(score: Score, inst: ScoreObjectInstance)

    fun removedObject(score: Score, inst: ScoreObjectInstance)

    fun movedObject(score: Score, inst: ScoreObjectInstance, oldPosition: ObjectPosition) {}
}