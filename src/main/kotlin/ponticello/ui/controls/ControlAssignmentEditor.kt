package ponticello.ui.controls

import fxutils.actions.ActionBar
import fxutils.button
import fxutils.hasFile
import fxutils.prompt.SimpleSearchableListView
import fxutils.setupDropArea
import fxutils.styleClass
import javafx.scene.Node
import javafx.scene.input.DragEvent
import javafx.scene.input.Dragboard
import javafx.scene.layout.HBox
import ponticello.model.obj.BufferObject
import ponticello.model.registry.BufferRegistry
import ponticello.model.registry.reference
import ponticello.model.score.ParameterControlList.NamedParameterControl
import ponticello.model.score.controls.BufferControl
import ponticello.model.score.controls.ParameterControl
import ponticello.ui.score.ScoreObjectView
import reaktive.value.now

class ControlAssignmentEditor(val control: NamedParameterControl, val view: ScoreObjectView?) : HBox() {
    private var selectedOption: ControlType<*>? = null
    private val optionButton = button(style = "selector-button")
    private val detailEditors = mutableMapOf<ControlType<*>, Node>()
    private var settingControl = false
    private var detailEditor: Node? = null

    init {
        optionButton.isFocusTraversable = false
        optionButton.setOnMouseClicked { showOptionPopup() }
        optionButton.prefWidth = 45.0
        setupDropArea(this::canDrop, ::onDrop)
        styleClass("control-detail-editor")
    }

    private fun onDrop(ev: DragEvent) {
        val db = ev.dragboard
        val samples = control.context[BufferRegistry]
        val sample =
            when {
                db.hasFile("wav") -> samples.getOrAdd(db.files[0])
                db.hasContent(BufferObject.DATA_FORMAT) -> samples.get(db.getContent(BufferObject.DATA_FORMAT) as String)
                else -> return
            }
        val ctrl = control.now as BufferControl
        ctrl.sample.set(sample.reference())
    }

    private fun canDrop(db: Dragboard): Boolean {
        if (control.now !is BufferControl) return false
        return db.hasFile("wav") || db.hasContent(BufferObject.DATA_FORMAT)
    }

    private fun showOptionPopup() {
        val spec = control.spec.now ?: return
        val options = ControlType.all.filter { option -> option.applicableOn(control.parentObject, spec) }
        if (options.isEmpty() || options.size == 1) return
        val listView = SimpleSearchableListView(options, "Select control type")
        val option = listView.showPopup(anchorNode = optionButton, initialOption = selectedOption) ?: return
        updateControlType(option)
        detailEditor?.requestFocus()
    }

    private fun <T : ParameterControl> updateControlType(t: ControlType<T>) {
        selectedOption = t
        val oldControl = control.now
        val newControl = t.createInitialControl(
            control.parentObject, control.spec.now, oldControl, control, anchorNode = optionButton
        )
        control.reassign(newControl)
        t.onSelected(control, newControl, view)
    }

    fun setControl(newControl: ParameterControl) {
        val type = ControlType.getType(newControl)
        settingControl = true
        optionButton.text = type.toString()
        detailEditor =
            if (control.spec.now == null) missingSpecOptionsBar(control)
            else type.createDetailInput(control, newControl, view)
        detailEditors[type] = detailEditor!!
        children.clear()
        children.add(optionButton)
        val actions = type.actions(control, newControl, view)
        children.add(detailEditor)
        if (actions.isNotEmpty()) children.add(ActionBar(actions, "medium-icon-button"))
        settingControl = false
    }
}