package ponticello.ui.controls

import fxutils.centerChildren
import fxutils.controls.SliderBar
import fxutils.label
import fxutils.sync
import fxutils.undo.UndoManager
import javafx.scene.Node
import javafx.scene.control.CheckBox
import javafx.scene.control.Label
import javafx.scene.layout.HBox
import javafx.scene.layout.Region
import ponticello.model.obj.ParameterizedObject
import ponticello.model.score.ParameterControlList.NamedParameterControl
import ponticello.model.score.controls.ParameterControl
import ponticello.model.score.controls.ValueControl
import ponticello.model.score.controls.getNumericalValue
import ponticello.sc.ControlSpec
import ponticello.sc.NumericalControlSpec
import ponticello.ui.score.ScoreObjectView
import reaktive.value.binding.map
import reaktive.value.now
import reaktive.value.reactiveVariable

data object ValueControlType : ControlType<ValueControl>() {
    override fun applicableOn(obj: ParameterizedObject, spec: ControlSpec): Boolean =
        spec is NumericalControlSpec

    override fun createDetailInput(
        namedControl: NamedParameterControl,
        control: ValueControl,
        view: ScoreObjectView?,
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

    override fun createSimpleInput(
        namedControl: NamedParameterControl, control: ValueControl,
    ): Node {
        val valueLabel = label(control.value.map { v -> v.toString() })
        return valueLabel
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