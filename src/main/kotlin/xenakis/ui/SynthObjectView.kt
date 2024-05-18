package xenakis.ui

import javafx.scene.paint.Color
import xenakis.model.SynthObject
import xenakis.ui.XenakisController.Companion.currentProject

class SynthObjectView(val obj: SynthObject) : ScoreObjectView(obj) {
    init {
        styleClass("synth-object")
    }

    override fun init(parent: ScoreView) {
        super.init(parent)
        val btn = Icon.Details.button(action = "Open control assignment view") {
            val confirmed = ControlAssignmentView.show(obj, context[currentProject])
            if (confirmed) {
                repaint()
            }
        }
        actions.children.add(0, btn)
    }

    override val defaultBorderColor: Color
        get() = obj.synthDef.associatedColor
}