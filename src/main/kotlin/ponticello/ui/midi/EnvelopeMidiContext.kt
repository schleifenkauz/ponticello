package ponticello.ui.midi

import ponticello.model.score.Envelope
import ponticello.sc.NumericalControlSpec

class EnvelopeMidiContext(
    private val envelope: Envelope,
    private val spec: NumericalControlSpec,
    private val focusedIndex: Int
) : AbstractMidiContext(envelope.context) {
    override fun cc(channel: Int, index: Int, value: Int) {
        if (focusedIndex == -1) return
        val idx = focusedIndex + index
        if (idx !in envelope.points.indices) return
        val valueBefore = envelope.points[idx].value
        val valueAfter = valueBefore.adjustByMidiDelta(value, spec, context)
        envelope.editPoint(idx, valueAfter)
    }
}