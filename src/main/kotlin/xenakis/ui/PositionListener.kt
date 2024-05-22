package xenakis.ui

import xenakis.model.ScoreObject

interface PositionListener {
    fun moved(obj: ScoreObject, start: Double, y: Double)
}