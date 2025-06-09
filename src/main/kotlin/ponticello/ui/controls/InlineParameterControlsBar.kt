package ponticello.ui.controls

import fxutils.label
import fxutils.prompt.SimpleSearchableListView
import fxutils.runFXWithTimeout
import fxutils.styleClass
import javafx.beans.InvalidationListener
import javafx.scene.Cursor
import javafx.scene.input.MouseButton
import javafx.scene.layout.HBox
import javafx.scene.layout.Region
import ponticello.model.project.InlineControlsDisplay
import ponticello.model.project.UIState
import ponticello.model.score.ParameterControlList
import ponticello.model.score.ParameterControlList.NamedParameterControl
import ponticello.model.score.controls.ParameterControl
import ponticello.sc.ControlSpec
import ponticello.ui.score.ScoreObjectView
import reaktive.value.binding.map
import reaktive.value.forEach
import reaktive.value.fx.asObservableValue
import reaktive.value.now

class InlineParameterControlsBar(
    private val controls: ParameterControlList,
    private val view: ScoreObjectView,
) : ParameterControlList.Listener, HBox() {
    private val boxes = mutableMapOf<NamedParameterControl, HBox>()
    private val controlMap = mutableMapOf<HBox, NamedParameterControl>()

    init {
        controls.addListener(this, initialize = true)
        visibleProperty().bind(view.context[UIState].controlsDisplay.map { mode ->
            mode in setOf(InlineControlsDisplay.EXTENDED_OVERLAY, InlineControlsDisplay.CONTROLS_BAR)
        }.asObservableValue())
        children.addListener(InvalidationListener {
            runFXWithTimeout(10) {
                view.updateInlineControlsVisibility()
            }
        })
    }

    override fun added(obj: NamedParameterControl, idx: Int) {
        val box = HBox() styleClass "simple-parameter-control-box"
        setControl(obj, box)
        boxes[obj] = box
        val displayInline = obj.spec.map { spec -> spec != null && spec.inlineDisplay }
        box.cursor = Cursor.DEFAULT
        box.userData = displayInline.forEach { display ->
            if (display) {
                val index = children.binarySearchBy(idx) { b -> controls.indexOf(controlMap[b]) }
                if (index < 0) children.add(-index - 1, box)
                else {}
            } else {
                children.remove(box)
            }
        }
    }

    override fun removed(obj: NamedParameterControl) {
        val box = boxes.getValue(obj)
        children.remove(box)
    }

    override fun moved(obj: NamedParameterControl, idx: Int) {
        val box = boxes.getValue(obj)
        children.remove(box)
        val childIdx = children.binarySearchBy(idx) { b -> controls.indexOf(controlMap[b]) }
        children.add(-childIdx - 1, box)
    }

    override fun reassignedControl(
        parameter: NamedParameterControl,
        oldControl: ParameterControl, newControl: ParameterControl,
    ) {
        val box = boxes.getValue(parameter)
        setControl(parameter, box)
    }

    private fun setControl(control: NamedParameterControl, box: HBox) {
        val type = ControlType.getType(control.now)
        val simpleInput = type.createSimpleInput(control, control.now)
        if (simpleInput == null) {
            box.children.clear()
            return
        }
        if (box.children.isEmpty()) {
            val nameLabel = label(control.name.map { name -> "$name: " }) styleClass "simple-parameter-control-name"
            nameLabel.setOnMouseClicked { ev ->
                when (ev.button) {
                    MouseButton.SECONDARY -> controls.remove(control)
                    MouseButton.PRIMARY -> showControlTypePopup(control, type, anchorNode = nameLabel)
                    else -> {}
                }
            }
            box.children.add(nameLabel)
        }
        box.children.remove(1, box.children.size)
        box.children.add(1, simpleInput)
    }

    private fun showControlTypePopup(
        control: NamedParameterControl, selectedType: ControlType<ParameterControl>,
        anchorNode: Region,
    ) {
        val spec = control.spec.now ?: return
        val options = ControlType.all.filter { option -> option.applicableOn(control.parentObject, spec) }
        if (options.isEmpty() || options.size == 1) return
        val listView = SimpleSearchableListView(options, "Select control type")
        val option = listView.showPopup(anchorNode, initialOption = selectedType) ?: return
        updateControlType(control, option, spec, anchorNode)
    }

    private fun <T : ParameterControl> updateControlType(
        control: NamedParameterControl, option: ControlType<T>, spec: ControlSpec, anchorNode: Region,
    ) {
        val oldControl = control.now
        val newControl = option.createInitialControl(controls.associatedObject, spec, oldControl, control, anchorNode)
        controls.reassignControl(control.name.now, newControl)
        option.onSelected(control, newControl, view)
    }
}