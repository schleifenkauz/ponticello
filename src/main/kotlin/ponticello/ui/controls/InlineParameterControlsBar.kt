package ponticello.ui.controls

import fxutils.label
import fxutils.prompt.SimpleSearchableListView
import fxutils.styleClass
import javafx.scene.Cursor
import javafx.scene.input.MouseButton
import javafx.scene.layout.HBox
import javafx.scene.layout.Region
import javafx.scene.text.Font
import ponticello.model.project.InlineControlsDisplay
import ponticello.model.project.UIState
import ponticello.model.score.ParameterControlList
import ponticello.model.score.ParameterControlList.NamedParameterControl
import ponticello.model.score.controls.ParameterControl
import ponticello.sc.ControlSpec
import ponticello.ui.score.ScoreObjectView
import reaktive.value.binding.map
import reaktive.value.fx.asObservableValue
import reaktive.value.now

class InlineParameterControlsBar(
    private val controls: ParameterControlList,
    private val view: ScoreObjectView,
) : ParameterControlList.Listener, HBox() {
    private val boxes = mutableMapOf<NamedParameterControl, HBox>()

    init {
        styleClass.add("parameter-controls-bar")
        cursor = Cursor.DEFAULT
        controls.addListener(this, initialize = true)
        visibleProperty().bind(view.context[UIState].controlsDisplay.map { mode ->
            mode in setOf(InlineControlsDisplay.EXTENDED_OVERLAY, InlineControlsDisplay.CONTROLS_BAR)
        }.asObservableValue())
    }

    override fun added(obj: NamedParameterControl, idx: Int) {
        val box = createBox(obj) ?: HBox()
        boxes[obj] = box
        children.add(idx, box)
    }

    override fun removed(obj: NamedParameterControl) {
        val box = boxes.getValue(obj)
        children.remove(box)
    }

    override fun moved(obj: NamedParameterControl, idx: Int) {
        val box = boxes.getValue(obj)
        children.remove(box)
        children.add(idx, box)
    }

    override fun reassignedControl(
        parameter: NamedParameterControl,
        oldControl: ParameterControl, newControl: ParameterControl,
    ) {
        val box = boxes.getValue(parameter)
        children.remove(box)
        val newBox = createBox(parameter) ?: HBox()
        boxes[parameter] = newBox
        children.add(newBox)
    }

    private fun createBox(obj: NamedParameterControl): HBox? {
        val type = ControlType.getType(obj.now)
        val simpleInput = type.createSimpleInput(obj, obj.now) ?: return null
        val nameLabel = label(obj.name.map { name -> "$name: " })
        nameLabel.font = Font.font(11.0)
        nameLabel.setOnMouseClicked { ev ->
            when (ev.button) {
                MouseButton.SECONDARY -> controls.remove(obj)
                MouseButton.PRIMARY -> showControlTypePopup(obj, type, anchorNode = nameLabel)
                else -> {}
            }
        }
        return HBox(nameLabel, simpleInput) styleClass "simple-parameter-control-box"
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