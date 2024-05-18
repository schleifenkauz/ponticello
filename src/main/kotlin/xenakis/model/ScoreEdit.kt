package xenakis.model

import hextant.undo.AbstractEdit
import hextant.undo.Edit
import xenakis.impl.Point

abstract class ScoreEdit(val score: Score) : AbstractEdit() {
    class AddObject(val obj: ScoreObject, score: Score) : ScoreEdit(score) {
        override val actionDescription: String
            get() = "Add object to score"

        override fun doRedo() {
            score.addObject(obj)
        }

        override fun doUndo() {
            score.removeObject(obj)
        }
    }

    class RemoveObjects(private val objects: List<ScoreObject>, score: Score) : ScoreEdit(score) {
        override val actionDescription: String
            get() = "Remove objects from score"

        override fun doUndo() {
            for (obj in objects) score.addObject(obj)
        }

        override fun doRedo() {
            for (obj in objects) score.removeObject(obj)
        }

        override fun mergeWith(other: Edit): Edit? {
            if (other !is RemoveObjects) return null
            if (other.score != this.score) return null
            return RemoveObjects(this.objects + other.objects, score)
        }
    }

    class AddTime(private val location: Double, private val amount: Double, score: Score) : ScoreEdit(score) {
        override val actionDescription: String
            get() = "Add $amount seconds at $location"

        override fun doRedo() {
            score.addTime(location, amount)
        }

        override fun doUndo() {
            score.deleteTimeRange(location, location + amount)
        }
    }

    class MoveObject(
        private val obj: ScoreObject,
        private val before: Point,
        private val after: Point,
        score: Score
    ) : ScoreEdit(score) {
        override val actionDescription: String
            get() = "Move object"

        override fun doRedo() {
            score.moveObject(obj, after.x, after.y)
        }

        override fun doUndo() {
            score.moveObject(obj, before.x, before.y)
        }

        override fun mergeWith(other: Edit): Edit? {
            if (other is MoveObject && other.obj == this.obj && other.score == this.score) {
                return MoveObject(obj, this.before, other.after, score)
            }
            return null
        }
    }
}