package xenakis.ui

import xenakis.impl.Point

interface EnvelopeView {
    fun addedPoint(idx: Int, point: Point)

    fun removedPoint(idx: Int, point: Point)

    fun changedPoint(idx: Int, newPoint: Point)
}