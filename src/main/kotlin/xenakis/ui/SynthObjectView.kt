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
        val btn = Icon.Details.button(action = "Open control assignment view") { openControlAssignment() }
        header.children.add(1, btn)
    }

    fun openControlAssignment() {
        val confirmed = ControlAssignmentView.show(obj, context[currentProject])
        if (confirmed) {
            repaint()
        }
    }

    override val defaultBorderColor: Color
        get() = obj.synthDef.associatedColor
}