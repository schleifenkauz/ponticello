package ponticello.model.score

import fxutils.undo.AbstractEdit
import javafx.scene.paint.Color
import reaktive.value.now

abstract class ScoreObjectEdit(protected val obj: ScoreObject) : AbstractEdit() {
    class Rename(val oldName: String, val newName: String, obj: ScoreObject) : ScoreObjectEdit(obj) {
        override val actionDescription: String
            get() = "Rename object"

        override fun doUndo() {
            obj.rename(oldName)
        }

        override fun doRedo() {
            obj.rename(newName)
        }
    }

    class Recolor(obj: ScoreObject, private val oldColor: Color?, private val newColor: Color?) : ScoreObjectEdit(obj) {
        override val actionDescription: String
            get() = "Recolor object"

        override fun doRedo() {
            obj.associatedColor.now = newColor
        }

        override fun doUndo() {
            obj.associatedColor.now = oldColor
        }
    }
}