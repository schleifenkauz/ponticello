package ponticello.ui.controls

import fxutils.actions.ContextualizedAction
import fxutils.actions.collectActions
import fxutils.actions.detailsAction
import fxutils.controls.CheckBox
import fxutils.controls.SliderBar
import fxutils.label
import fxutils.opacity
import fxutils.undo.UndoManager
import fxutils.undo.VariableEdit
import hextant.context.Context
import hextant.context.compoundEdit
import javafx.scene.Cursor
import javafx.scene.Node
import javafx.scene.control.Label
import javafx.scene.input.MouseButton
import javafx.scene.layout.Region
import ponticello.impl.Decimal
import ponticello.impl.DecimalRange
import ponticello.impl.Logger
import ponticello.model.obj.ParameterizedObject
import ponticello.model.score.ParameterControlList.NamedParameterControl
import ponticello.model.score.controls.ParameterControl
import ponticello.model.score.controls.ValueControl
import ponticello.model.score.controls.getNumericalValue
import ponticello.sc.ControlSpec
import ponticello.sc.NumericalControlSpec
import ponticello.sc.Transformation
import ponticello.sc.mapOnto
import ponticello.ui.impl.DEFAULT_SCENE_FILL
import ponticello.ui.launcher.PonticelloApp.Companion.primaryStage
import ponticello.ui.score.ScoreObjectView
import reaktive.value.binding.map
import reaktive.value.now

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
        return sliderBar
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

    override fun supportsDialogInput(): Boolean = true

    override fun showDialogInput(
        parameterName: String, specs: List<ControlSpec>, controls: List<ValueControl>, context: Context
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
            .showDialog(context[primaryStage], null) ?: return false
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
        anchorNode: Region?,
    ): ValueControl {
        spec as NumericalControlSpec
        val value = oldControl?.getNumericalValue() ?: spec.defaultValue.get()
        return ValueControl.create(value)
    }

    override fun actions(
        namedControl: NamedParameterControl, control: ValueControl, view: ScoreObjectView?,
    ): List<ContextualizedAction> = actions.withContext(control)

    override fun toString(): String = "Num"

    private val actions = collectActions<ValueControl> {
        add(
            detailsAction(
                sceneFill = DEFAULT_SCENE_FILL.opacity(0.5),
                labelWidth = 100.0
            ) { control ->
                CheckBox(control.allocateBus)
                    .setupUndo(control.context[UndoManager], variableDescription = "Allocate bus")
                    .named("Allocate bus")
            })
    }
}