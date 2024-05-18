package xenakis.model

import hextant.undo.AbstractEdit

abstract class ScoreObjectEdit(protected val obj: ScoreObject) : AbstractEdit() {
    class Rename(val oldName: String, val newName: String, obj: ScoreObject) : ScoreObjectEdit(obj) {
        override val actionDescription: String
            get() = "Rename object"

        override fun doUndo() {
            obj.name = oldName
        }

        override fun doRedo() {
            obj.name = newName
        }
    }

    class Mute(val value: Boolean, obj: ScoreObject) : ScoreObjectEdit(obj) {
        override val actionDescription: String
            get() = if (value) "Mute" else "Unmute"

        override fun doUndo() {
            obj.muted = !value
        }

        override fun doRedo() {
            obj.muted = value
        }
    }

    class ReassignControls(
        private val oldControls: List<ParameterControl>,
        private val newControls: List<ParameterControl>,
        obj: ScoreObject
    ) : ScoreObjectEdit(obj) {
        override val actionDescription: String
            get() = "Reassign controls"

        override fun doUndo() {
            obj.controls = oldControls
        }

        override fun doRedo() {
            obj.controls = newControls
        }
    }
}