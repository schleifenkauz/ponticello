package xenakis.ui

import bundles.createBundle
import hextant.undo.UndoManager
import javafx.geometry.Point2D
import javafx.scene.control.ContextMenu
import javafx.scene.control.MenuItem
import javafx.scene.input.MouseEvent
import javafx.scene.layout.HBox
import javafx.scene.paint.Color
import reaktive.Observer
import reaktive.value.now
import xenakis.model.Envelope
import xenakis.model.EnvelopeControl
import xenakis.model.SynthObject
import xenakis.sc.NumericalControlSpec
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
        setupGroupSelector()
        setupSynthDefReference()
        listenForMouseEvents()
    }

    private fun setupSynthDefReference() {
        val nameLabel = label(obj.synthDef.name) styleClass "synth-def-ref-label"
        val viewBtn = Icon.View.button(action = "View SynthDef") {
            context[SynthDefRegistryPane].editSynthDef(obj.synthDef)
        }
        val box = HBox(nameLabel, viewBtn) styleClass "synth-def-ref-box"
        header.children.add(1, box)
    }

    private fun setupGroupSelector() {
        groupSelector = GroupSelector(context, obj.group)
        groupSelectorControl = GroupSelectorControl(groupSelector, createBundle())
        obs = groupSelector.result.observe { _, _, group ->
            if (obj.group != group && group != GroupSelectorControl.createNew) {
                obj.group = group
            }
        }
        header.children.add(1, groupSelectorControl)
    }

    fun changedGroup() {
        if (groupSelector.result.now != obj.group) groupSelector.select(obj.group)
    }

    private fun listenForMouseEvents() {
        addEventHandler(MouseEvent.MOUSE_CLICKED) { ev ->
            if (ev.clickCount >= 2 && obj.controls.isNotEmpty()) {
                openControlAssignment()
                ev.consume()
            }
            if (ev.isAltDown) {
                val p = localToScreen(ev.x, ev.y)
                showNewEnvelopePopup(p)
                ev.consume()
            }
        }
    }

    private fun showNewEnvelopePopup(point: Point2D) {
        val possibleParameters = obj.synthDef.parameters.now
            .filter { p -> p.spec.now is NumericalControlSpec }
            .filter { p ->
                val control = obj.controls[p.name.now]
                control !is EnvelopeControl || !control.display
            }
        val menu = ContextMenu()
        for (p in possibleParameters) {
            val name = p.name.now
            val spec = p.spec.now as NumericalControlSpec
            val item = MenuItem(name)
            item.setOnAction {
                val oldControl = obj.controls[p.name.now]
                val env =
                    if (oldControl is EnvelopeControl) oldControl.envelope
                    else Envelope.constant(spec.defaultValue.get(), obj.duration, spec.warp)
                val control = EnvelopeControl(env, spec.associatedColor, display = true)
                obj.reassignControl(name, control)
            }
            menu.items.add(item)
        }
        menu.isAutoHide = true
        menu.show(scene.window, point.x, point.y)
    }

    fun openControlAssignment() {
        //TODO: Highlight unused controls in assignment view with ability to remove them
        cleanupControls()
        context[UndoManager].finishCompoundEdit()
        ControlAssignmentView.show(obj, context[currentProject])
    }

    private fun cleanupControls() {
        context[UndoManager].beginCompoundEdit("Adjust controls to changes in SynthDef")
        val parameters = obj.synthDef.parameters.now
        val parameterNames = parameters.map { p -> p.name.now }
        for ((parameter, _) in obj.controls.toMap()) {
            if (parameter !in parameterNames) obj.removeControl(parameter)
        }
        for (param in parameters) {
            val name = param.name.now
            if (name !in obj.controls.keys) {
                val control = param.defaultControl()
                obj.addControl(name, control)
            }
        }
    }

    override val defaultBackgroundColor: Color
        get() = obj.synthDef.color.now
}