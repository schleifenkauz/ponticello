package xenakis.model

import hextant.undo.AbstractEdit
import hextant.undo.Edit

abstract class ScoreEdit(val score: Score) : AbstractEdit() {
    class AddObject(val obj: ScoreObjectInstance, score: Score) : ScoreEdit(score) {
        override val actionDescription: String
            get() = "Add object to score"

        override fun doRedo() {
            score.addObject(obj)
        }

        override fun doUndo() {
            score.removeObject(obj)
        }
    }

    class RemoveObjects(private val objects: Set<ScoreObjectInstance>, score: Score) : ScoreEdit(score) {
        override val actionDescription: String
            get() = "Remove objects from score"

        override fun doUndo() {
            for (obj in objects) score.addObject(obj)
        }

        override fun doRedo() {
            score.removeObjects(objects)
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
}