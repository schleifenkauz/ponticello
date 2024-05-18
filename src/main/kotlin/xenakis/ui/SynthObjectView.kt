package xenakis.ui

import javafx.scene.paint.Color
import xenakis.model.SynthObject
import xenakis.model.XenakisProject

class SynthObjectView(override val obj: SynthObject, project: XenakisProject) : ScoreObjectView() {
    init {
        styleClass("synth-object")
        addAction(Icon.Details, "Open control assignment view") {
            val confirmed = ControlAssignmentView.show(obj, project)
            if (confirmed) {
                repaint()
            }
        }
    }

    override val defaultBorderColor: Color
        get() = obj.synthDef.associatedColor
}