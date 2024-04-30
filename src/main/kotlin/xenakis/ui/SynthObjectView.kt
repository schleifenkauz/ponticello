package xenakis.ui

import xenakis.model.SynthObject
import xenakis.model.XenakisProject

class SynthObjectView(override val obj: SynthObject, project: XenakisProject) : ScoreObjectView(obj, project) {
    init {
        styleClass("synth-object")
    }

    override fun repaint() {
        super.repaint()
        addAction(Icon.Details, "Open control assignment view") {
            val confirmed = ControlAssignmentView.show(obj, project)
            if (confirmed) {
                repaint()
            }
        }
    }
}