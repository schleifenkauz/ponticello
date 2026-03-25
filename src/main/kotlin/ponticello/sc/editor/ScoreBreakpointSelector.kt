package ponticello.sc.editor

import ponticello.model.obj.project
import ponticello.model.project.OBJECTS
import ponticello.model.project.get
import ponticello.model.score.ScoreBreakpointObject

class ScoreBreakpointSelector : ObjectSelector<ScoreBreakpointObject>() {
    override fun getOptions(): List<ScoreBreakpointObject> =
        context.project[OBJECTS].filterIsInstance<ScoreBreakpointObject>()

    override fun createNewObject(name: String): ScoreBreakpointObject? = null
}