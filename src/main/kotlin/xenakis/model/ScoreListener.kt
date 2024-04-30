package xenakis.model

interface ScoreListener {
    fun addedObject(obj: ScoreObject)

    fun removedObject(obj: ScoreObject)

    fun setTotalDuration(duration: Double)

    fun movedObject(obj: ScoreObject)
}