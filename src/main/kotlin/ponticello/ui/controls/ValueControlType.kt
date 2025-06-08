package ponticello.ui.controls

import fxutils.centerChildren
import fxutils.controls.SliderBar
import fxutils.label
import fxutils.sync
import fxutils.undo.UndoManager
import fxutils.undo.VariableEdit
import javafx.scene.Cursor
import javafx.scene.Node
import javafx.scene.control.CheckBox
import javafx.scene.control.Label
import javafx.scene.input.MouseButton
import javafx.scene.layout.HBox
import javafx.scene.layout.Region
import ponticello.impl.Decimal
import ponticello.model.obj.ParameterizedObject
import ponticello.model.score.ParameterControlList.NamedParameterControl
import ponticello.model.score.controls.ParameterControl
import ponticello.model.score.controls.ValueControl
import ponticello.model.score.controls.getNumericalValue
import ponticello.sc.ControlSpec
import ponticello.sc.NumericalControlSpec
import ponticello.sc.Transformation
import ponticello.sc.mapOnto
import ponticello.ui.score.ScoreObjectView
import reaktive.value.binding.map
import reaktive.value.now
import reaktive.value.reactiveVariable

data object ValueControlType : ControlType<ValueControl>() {
    override fun applicableOn(obj: ParameterizedObject, spec: ControlSpec): Boolean =
        spec is NumericalControlSpec

    override fun createDetailInput(
        namedControl: NamedParameterControl, control: ValueControl, view: ScoreObjectView?,
    ): Node {
        val spec = namedControl.spec.now
        if (spec !is NumericalControlSpec) return missingSpecOptionsBar(namedControl)
        val converter = spec.converter()
        val sliderBar = SliderBar(
            control.value, namedControl.name, converter,
            style = SliderBar.Style.AlwaysValue,
            undoManager = namedControl.context[UndoManager]
        )
        sliderBar.prefWidth = 150.0
        val allocateBusOption = CheckBox()
            .sync(control.allocateBus, description = "Allocate bus", namedControl.context[UndoManager])
        return HBox(5.0, sliderBar, Label("Allocate bus"), allocateBusOption).centerChildren()
    }

    override fun createSimpleInput(namedControl: NamedParameterControl, control: ValueControl): Node {
        val valueLabel = label(control.value.map { v -> v.toString() })
        valueLabel.cursor = Cursor.V_RESIZE
        setupValueDragging(valueLabel, namedControl, control)
        valueLabel.setOnMouseClicked { ev ->
            if (ev.clickCount == 2 && ev.button == MouseButton.PRIMARY) {
                val spec = namedControl.spec.now as? NumericalControlSpec ?: return@setOnMouseClicked
                val oldValue = control.value.now
                val newValue = DecimalPrompt(namedControl.name.now, oldValue, spec.range)
                    .showDialog(anchorNode = valueLabel) ?: return@setOnMouseClicked
                val actionDescription = "Update ${namedControl.name.now}"
                VariableEdit.updateVariable(control.value, newValue, control.context[UndoManager], actionDescription)
            }
        }
        return valueLabel
    }

    private const val DRAG_RANGE = 300.0

    private fun setupValueDragging(valueLabel: Label, namedControl: NamedParameterControl, control: ValueControl) {
        var dragStartY = Double.NaN
        var valueBefore = Decimal.NaN
        lateinit var spec: NumericalControlSpec
        lateinit var transformation: Transformation
        valueLabel.setOnDragDetected { ev ->
            spec = namedControl.spec.now as? NumericalControlSpec ?: return@setOnDragDetected
            transformation = spec.mapOnto(DRAG_RANGE, -DRAG_RANGE)
            valueBefore = control.value.now
            println("Drag detected")
            dragStartY = ev.screenY
            valueLabel.startFullDrag()
            ev.consume()
        }
        valueLabel.setOnMouseDragged { ev ->
            if (dragStartY.isNaN()) return@setOnMouseDragged
            val deltaY = (ev.screenY - dragStartY)
            val value = transformation.unmap(deltaY + transformation.map(valueBefore.value))
                .coerceIn(spec.min.get().value, spec.max.get().value)
            control.value.set(Decimal(value, precision = spec.precision))
            ev.consume()
        }
        valueLabel.setOnMouseReleased { ev ->
            if (dragStartY.isNaN()) return@setOnMouseReleased
            val value = control.value.now
            if (value != valueBefore) {
                val actionDescription = "Update ${namedControl.name.now}"
                control.context[UndoManager].record(VariableEdit(control.value, valueBefore, actionDescription))
            }
            dragStartY = Double.NaN
            valueBefore = Decimal.NaN
            ev.consume()
        }
    }

    override fun createInitialControl(
        obj: ParameterizedObject,
        spec: ControlSpec?,
        oldControl: ParameterControl,
        namedControl: NamedParameterControl,
        anchorNode: Region,
    ): ValueControl {
        spec as NumericalControlSpec
        return ValueControl(reactiveVariable(oldControl.getNumericalValue() ?: spec.defaultValue.get()))
    }

    override fun toString(): String = "Num"
}