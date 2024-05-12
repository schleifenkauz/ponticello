package xenakis.model

interface ScoreListener {
    fun addedObject(obj: ScoreObject)

    fun removedObject(obj: ScoreObject)

    fun movedObject(obj: ScoreObject)

    fun recolor(obj: SynthObject)
}