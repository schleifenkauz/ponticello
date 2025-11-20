package ponticello.ui.controls

import fxutils.actions.ActionBar
import fxutils.alwaysHGrow
import fxutils.infiniteSpace
import fxutils.prompt.SimpleSelectorPrompt
import fxutils.selectorButton
import fxutils.styleClass
import javafx.event.Event
import javafx.scene.Node
import javafx.scene.layout.HBox
import ponticello.model.score.controls.ParameterControl
import ponticello.model.score.controls.ParameterControlList.NamedParameterControl
import ponticello.ui.score.ScoreObjectView
import reaktive.value.now

class ControlAssignmentEditor(val control: NamedParameterControl, val view: ScoreObjectView?) : HBox() {
    private var selectedOption: ControlType<*>? = null
    private val optionButton = selectorButton()
    private val detailEditors = mutableMapOf<ControlType<*>, Node>()
    private var settingControl = false
    private var detailEditor: Node? = null

    init {
        optionButton.isFocusTraversable = false
        optionButton.setOnMouseClicked { ev -> showOptionPopup(ev) }
        optionButton.prefWidth = 55.0
        styleClass("parameter-control-item")
        alwaysHGrow()
    }
//
//    override fun drop(event: DragEvent): Boolean {
//        val db = event.dragboard
//        val samples = control.context[BufferRegistry]
//        val sample =
//            when {
//                db.hasFile(*SampleObject.SUPPORTED_AUDIO_FORMATS) -> samples.getOrAdd(db.files[0])
//                db.hasContent(BufferObject.DATA_FORMAT) -> samples.get(db.getContent(BufferObject.DATA_FORMAT) as String)
//                else -> return false
//            }
//        val ctrl = control.now as BufferControl
//        ctrl.sample.set(sample.reference())
//        return true
//    }
//
//    override fun acceptedTransferModes(event: DragEvent): Array<TransferMode> {
//        if (control.now !is BufferControl) return emptyArray()
//        val db = event.dragboard
//        return db.hasFile(*SampleObject.SUPPORTED_AUDIO_FORMATS) || db.hasContent(BufferObject.DATA_FORMAT)
//    }

    fun showOptionPopup(ev: Event?) {
        val spec = control.spec.now ?: return
        val options = ControlType.all.filter { option -> option.applicableOn(control.parentObject, spec) }
        if (options.isEmpty() || options.size == 1) return
        val listView = SimpleSelectorPrompt(options, "Select control type")
        val option = listView.showPopup(ev, initialOption = selectedOption) ?: return
        updateControlType(option, ev)
        detailEditor?.requestFocus()
    }

    private fun <T : ParameterControl> updateControlType(t: ControlType<T>, ev: Event?) {
        selectedOption = t
        val oldControl = control.now
        val newControl = t.createInitialControl(
            control.parentObject, control.spec.now, oldControl, control.name.now, ev
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
        if (actions.isNotEmpty()) {
            children.addAll(
                infiniteSpace(),
                ActionBar(actions, "medium-icon-button")
            )
        }
        settingControl = false
    }
}