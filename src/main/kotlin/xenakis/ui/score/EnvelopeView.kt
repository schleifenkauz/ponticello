package xenakis.ui.score

import xenakis.model.score.Envelope.EnvelopePoint

interface EnvelopeView {
    fun addedPoint(idx: Int, point: EnvelopePoint)

    fun removedPoint(idx: Int, point: EnvelopePoint)

    fun changedPoint(idx: Int, newPoint: EnvelopePoint)
}