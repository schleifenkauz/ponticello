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

    class MoveObjects(
        private val objects: Set<ScoreObjectInstance>,
        private val deltaT: Decimal, private val deltaY: Decimal
    ) : AbstractEdit() {
        override val actionDescription: String
            get() = "Move object"

        override fun doRedo() {
            for (obj in objects) {
                obj.moveTo(obj.start + deltaT, obj.y + deltaY, simpleMove = true)
            }
        }

        override fun doUndo() {
            for (obj in objects) {
                obj.moveTo(obj.start - deltaT, obj.y - deltaY, simpleMove = true)
            }
        }

        override fun mergeWith(other: Edit): Edit? {
            if (other is MoveObjects && other.objects == this.objects) {
                return MoveObjects(objects, deltaT + other.deltaT, deltaY + other.deltaY)
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