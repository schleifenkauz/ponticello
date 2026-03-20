package ponticello.ui.controls

import fxutils.controls.SliderBar
import fxutils.prompt.PromptPlacement
import fxutils.prompt.YesNoPrompt
import fxutils.undo.UndoManager
import fxutils.undo.VariableEdit
import hextant.context.Context
import hextant.context.compoundEdit
import javafx.scene.Cursor
import javafx.scene.Node
import javafx.scene.control.Label
import javafx.scene.input.MouseButton
import ponticello.impl.Decimal
import ponticello.impl.DecimalRange
import ponticello.impl.Logger
import ponticello.impl.zero
import ponticello.model.instr.ParameterizedObject
import ponticello.model.score.controls.ParameterControl
import ponticello.model.score.controls.ParameterControlList.NamedParameterControl
import ponticello.model.score.controls.ValueControl
import ponticello.model.score.controls.getNumericalValue
import ponticello.sc.*
import ponticello.ui.launcher.PonticelloApp.Companion.primaryStage
import ponticello.ui.score.ScoreObjectView
import reaktive.value.forEach
import reaktive.value.now

data object ValueControlType : ControlType<ValueControl>() {
    override fun applicableOn(obj: ParameterizedObject, spec: ControlSpec): Boolean =
        spec is NumericalControlSpec

    override fun createDetailInput(
        namedControl: NamedParameterControl, control: ValueControl, view: ScoreObjectView?,
    ): Node {
        val spec = namedControl.spec.now
        if (spec !is NumericalControlSpec) return missingSpecOptionsBar(namedControl)
        lateinit var bar: SliderBar<*>
        val converter = spec.converter(updateRange = { min, max ->
            val extendRange = YesNoPrompt("Extend parameter range?", default = true)
                .showDialog(bar)
            if (extendRange == true) {
                val newSpec = spec.copy(min = DecimalLiteral(min), max = DecimalLiteral(max))
                namedControl.setCustomSpec(newSpec)
                true
            } else false
        })
        bar = SliderBar(
            control.value, namedControl.name, converter,
            style = SliderBar.Style.AlwaysValue,
            undoManager = namedControl.context[UndoManager]
        )
        return bar
    }

    override fun createSimpleInput(namedControl: NamedParameterControl, control: ValueControl): Node {
        val valueLabel = Label()
        valueLabel.userData = control.value.forEach { v -> valueLabel.text = v.toString() }
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

    override fun supportsDialogInput(): Boolean = true

    override fun showDialogInput(
        parameterName: String, specs: List<ControlSpec>, controls: List<ValueControl>, context: Context,
    ): Boolean {
        val numericalSpecs = specs.filterIsInstance<NumericalControlSpec>()
        if (numericalSpecs.size != specs.size) {
            Logger.warn("Some specs are not numerical control specs", Logger.Category.Score)
            return false
        }
        val min = numericalSpecs.maxOf { spec -> spec.min.get() }
        val max = numericalSpecs.minOf { spec -> spec.max.get() }
        if (min > max) {
            Logger.warn("Invalid numerical range: $min > $max", Logger.Category.Score)
            return false
        }
        val precision = numericalSpecs.maxOf { spec -> spec.precision }
        val initialValue = controls.map { c -> c.value.now }.distinct().singleOrNull()
        val newValue = DecimalPrompt(parameterName, precision, initialValue, DecimalRange(min, max))
            .showDialog(context[primaryStage]) ?: return false
        context.compoundEdit("Update $parameterName") {
            for (ctrl in controls) {
                VariableEdit.updateVariable(ctrl.value, newValue, context[UndoManager], "Update $parameterName")
            }
        }
        return true
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
        oldControl: ParameterControl?,
        parameterName: String,
        promptPlacement: PromptPlacement?,
    ): ValueControl {
        val defaultValue = (spec as? NumericalControlSpec)?.defaultValue?.get() ?: zero
        val value = oldControl?.getNumericalValue() ?: defaultValue
        return ValueControl.create(value)
    }

    override fun toString(): String = "Num"
}