package xenakis.ui

import bundles.createBundle
import javafx.scene.paint.Color
import reaktive.Observer
import reaktive.value.now
import xenakis.model.SynthObject
import xenakis.model.defaultControls
import xenakis.sc.editor.GroupSelector
import xenakis.sc.view.GroupSelectorControl
import xenakis.ui.XenakisController.Companion.currentProject

class SynthObjectView(val obj: SynthObject) : ScoreObjectView(obj) {
    private lateinit var groupSelector: GroupSelector
    private lateinit var groupSelectorControl: GroupSelectorControl
    private lateinit var obs: Observer

    init {
        styleClass("synth-object")
    }

    override fun initialize(parent: ScorePane) {
        super.initialize(parent)
        val btn = Icon.Details.button(action = "Open control assignment view") { openControlAssignment() }
        header.children.add(1, btn)
        groupSelector = GroupSelector(context, obj.group)
        groupSelectorControl = GroupSelectorControl(groupSelector, createBundle())
        obs = groupSelector.result.observe { _, _, group -> obj.group = group }
        header.children.add(1, groupSelectorControl)
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
        ControlAssignmentView.show(obj, updatedControls, context[currentProject])
    }

    fun changedGroup() {
        if (groupSelector.result.now != obj.group) groupSelector.select(obj.group)
    }

    override val defaultBorderColor: Color
        get() = obj.synthDef.associatedColor
}