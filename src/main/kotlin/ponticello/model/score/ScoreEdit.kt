package ponticello.model.score

import fxutils.undo.AbstractEdit
import fxutils.undo.Edit
import ponticello.impl.Decimal

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

    class MoveObject(
        private val obj: ScoreObjectInstance,
        private val before: ObjectPosition,
        private val after: ObjectPosition,
    ) : AbstractEdit() {
        override val actionDescription: String
            get() = "Move object"

        override fun doRedo() {
            obj.moveTo(after.time, after.y, simpleMove = true)
        }

        override fun doUndo() {
            obj.moveTo(before.time, before.y, simpleMove = true)
        }

        override fun mergeWith(other: Edit): Edit? {
            if (other is MoveObject && other.obj == this.obj) {
                return MoveObject(obj, this.before, other.after)
            }
            return null
        }
    }

    class ToggleMute(private val obj: ScoreObjectInstance) : AbstractEdit() {
        override val actionDescription: String
            get() = "Toggle Muted"

        override fun doUndo() {
            obj.toggleMuted()
        }

        override fun doRedo() {
            obj.toggleMuted()
        }
    }
}