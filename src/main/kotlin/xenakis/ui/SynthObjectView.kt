package xenakis.ui

import javafx.scene.paint.Color
import xenakis.model.SynthObject
import xenakis.model.defaultControls
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
        val updatedControls = obj.controls.toMutableList()
        val default = obj.synthDef.defaultControls()
        updatedControls.removeIf { myCtrl -> default.none { ctrl -> ctrl.parameter == myCtrl.parameter } }
        for (control in default) {
            if (obj.controls.none { it.parameter == control.parameter }) {
                updatedControls.add(control)
            }
        }
        val confirmed = ControlAssignmentView.show(obj, updatedControls, context[currentProject])
        if (confirmed) {
            repaint()
        }
    }

    override val defaultBorderColor: Color
        get() = obj.synthDef.associatedColor
}