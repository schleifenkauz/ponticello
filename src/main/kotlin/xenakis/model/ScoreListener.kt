package xenakis.model

interface ScoreListener {
    fun addedObject(obj: ScoreObject)

    fun removedObject(obj: ScoreObject)

    fun chained(previous: ScoreObject, next: ScoreObject)

    fun unchained(previous: ScoreObject, next: ScoreObject)
}