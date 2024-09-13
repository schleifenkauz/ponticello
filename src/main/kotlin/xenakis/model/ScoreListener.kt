package xenakis.model

interface ScoreListener {
    fun addedObject(obj: ScoreObjectInstance)

    fun removedObject(obj: ScoreObjectInstance)
}