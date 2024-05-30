package xenakis.model

import hextant.undo.AbstractEdit

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

    class ReassignControl(
        private val parameter: String,
        private val oldControl: ParameterControl,
        private val newControl: ParameterControl,
        private val synthObject: SynthObject
    ) : ScoreObjectEdit(synthObject) {
        override val actionDescription: String
            get() = "Reassign controls"

        override fun doUndo() {
            synthObject.reassignControl(parameter, oldControl)
        }

        override fun doRedo() {
            synthObject.reassignControl(parameter, newControl)
        }
    }

    class AddControl(
        private val parameter: String,
        private val control: ParameterControl,
        private val synthObject: SynthObject
    ) : ScoreObjectEdit(synthObject) {
        override val actionDescription: String
            get() = "Add control"

        override fun doUndo() {
            synthObject.addControl(parameter, control)
        }

        override fun doRedo() {
            synthObject.removeControl(parameter)
        }
    }

    class RemoveControl(
        private val parameter: String,
        private val control: ParameterControl,
        private val synthObject: SynthObject
    ) : ScoreObjectEdit(synthObject) {
        override val actionDescription: String
            get() = "Remove control"

        override fun doUndo() {
            synthObject.addControl(parameter, control)
        }

        override fun doRedo() {
            synthObject.removeControl(parameter)
        }
    }
}