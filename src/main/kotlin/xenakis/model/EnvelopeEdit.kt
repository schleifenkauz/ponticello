package xenakis.model

import hextant.undo.AbstractEdit
import hextant.undo.Edit
import xenakis.impl.Point

abstract class EnvelopeEdit(protected val envelope: Envelope) : AbstractEdit() {
    class AddPoint(val point: Point, val idx: Int, envelope: Envelope) : EnvelopeEdit(envelope) {
        override val actionDescription: String
            get() = "Add envelope point"

        override fun doUndo() {
            envelope.removePoint(idx)
        }

        override fun doRedo() {
            envelope.addPoint(idx, point)
        }
    }

    class RemovePoint(val point: Point, val idx: Int, envelope: Envelope) : EnvelopeEdit(envelope) {
        override val actionDescription: String
            get() = "Remove envelope point"

        override fun doUndo() {
            envelope.addPoint(idx, point)
        }

        override fun doRedo() {
            envelope.removePoint(idx)
        }
    }

    class EditPoint(
        private val idx: Int,
        private val oldPoint: Point,
        private val newPoint: Point,
        envelope: Envelope
    ) : EnvelopeEdit(envelope) {
        override val actionDescription: String
            get() = "Edit envelope point"

        override fun doUndo() {
            envelope.editPoint(idx, oldPoint)
        }

        override fun doRedo() {
            envelope.editPoint(idx, newPoint)
        }

        override fun mergeWith(other: Edit): Edit? {
            if (other !is EditPoint) return null
            if (other.envelope != this.envelope) return null
            if (other.idx != this.idx) return null
            return EditPoint(idx, this.oldPoint, other.newPoint, envelope)
        }
    }
}