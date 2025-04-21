package xenakis.model.score

import hextant.undo.AbstractEdit
import hextant.undo.Edit
import xenakis.impl.Decimal

abstract class ScoreEdit(val score: Score) : AbstractEdit() {
    class AddObject(val obj: ScoreObjectInstance, score: Score) : ScoreEdit(score) {
        override val actionDescription: String
            get() = "Add object to score"

        override fun doRedo() {
            score.addObject(obj, autoSelect = false)
        }

        override fun doUndo() {
            score.removeObject(obj, option = Score.RegistryOption.REMOVE_WITHOUT_ASKING)
        }
    }

    class RemoveObjects(
        private val objects: Set<ScoreObjectInstance>,
        private val option: Score.RegistryOption,
        score: Score,
    ) : ScoreEdit(score) {
        override val actionDescription: String
            get() = "Remove objects from score"

        override fun doUndo() {
            for (obj in objects) score.addObject(obj, autoSelect = false)
        }

        override fun doRedo() {
            score.removeObjects(objects, option)
        }

        override fun mergeWith(other: Edit): Edit? {
            if (other !is RemoveObjects) return null
            if (other.score != this.score) return null
            if (other.option != this.option) return null
            return RemoveObjects(this.objects + other.objects, other.option, score)
        }
    }

    class AddTime(private val location: Decimal, private val amount: Decimal, score: Score) : ScoreEdit(score) {
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