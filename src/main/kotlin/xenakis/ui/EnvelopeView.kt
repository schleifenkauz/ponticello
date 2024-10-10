package xenakis.ui

import xenakis.model.Envelope.EnvelopePoint

interface EnvelopeView {
    fun addedPoint(idx: Int, point: EnvelopePoint)

    fun removedPoint(idx: Int, point: EnvelopePoint)

    fun changedPoint(idx: Int, newPoint: EnvelopePoint)
}