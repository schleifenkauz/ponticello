package xenakis.model

interface PositionListener {
    fun moved(obj: ScoreObject, start: Double, y: Double)
}