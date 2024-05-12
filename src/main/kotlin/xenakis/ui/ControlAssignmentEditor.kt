package xenakis.ui

import bundles.createBundle
import hextant.core.view.SimpleChoiceEditorControl
import javafx.collections.FXCollections.observableList
import javafx.geometry.Pos
import javafx.scene.Node
import javafx.scene.control.*
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.Region
import javafx.scene.paint.Color
import org.controlsfx.control.ToggleSwitch
import reaktive.value.now
import xenakis.model.*
import xenakis.sc.*
import xenakis.sc.editor.BufferRefEditor
import xenakis.sc.editor.BusRefEditor
import xenakis.sc.view.BusRefEditorControl

class ControlAssignmentEditor(private val parameter: ParameterDef, val project: XenakisProject) : HBox(5.0) {
    private val nameLabel = Label(parameter.name.text).also { l -> l.styleClass.add("control-label") }
    private val comboBox = ComboBox(observableList(ControlType.all))
    private val detailEditors = mutableMapOf<ControlType<*, *>, Node>()
    private var detailEditor: Node? = null
        set(value) {
            field = value!!
            value.styleClass?.add("control-detail-editor")
            if (parameter.spec is NumericalControlSpec) {
                children.setAll(nameLabel, comboBox, detailEditor)
            } else {
                children.setAll(nameLabel, detailEditor)
            }
            setHgrow(value, Priority.ALWAYS)
        }

    init {
        styleClass.add("control-assignment-editor")
        comboBox.styleClass.add("control-option-selector")
        comboBox.valueProperty().addListener { _, _, t ->
            detailEditor = detailEditors.getOrPut(t) { t.createDetailInput(parameter, null, project) }
        }
    }

    fun setControl(control: ParameterControl) {
        val type = ControlType.getType(control)
        comboBox.value = type
        detailEditor = type.createDetailInput(parameter, control, project)
        detailEditors[type] = detailEditor!!
    }

    fun createControl(): ParameterControl {
        @Suppress("UNCHECKED_CAST")
        val type = comboBox.value!! as ControlType<ParameterControl, Node>
        return type.createControl(detailEditor!!, parameter)
    }

    sealed class ControlType<C : ParameterControl, I : Node> {
        abstract fun createDetailInput(parameter: ParameterDef, control: C?, project: XenakisProject): I

        abstract fun createControl(detailInput: I, parameter: ParameterDef): C

        override fun toString(): String = this::class.simpleName!!

        object Constant : ControlType<ConstantControl, Spinner<Double>>() {
            override fun createDetailInput(
                parameter: ParameterDef,
                control: ConstantControl?,
                project: XenakisProject,
            ): Spinner<Double> {
                val spec = parameter.spec as NumericalControlSpec
                return Spinner(spec.min.value, spec.max.value, control?.value ?: spec.defaultValue.value)
            }

            override fun createControl(detailInput: Spinner<Double>, parameter: ParameterDef) =
                ConstantControl(parameter.name.text, detailInput.value)
        }

        object Knob : ControlType<KnobControl, Slider>() {
            override fun createDetailInput(
                parameter: ParameterDef,
                control: KnobControl?,
                project: XenakisProject,
            ): Slider {
                val spec = parameter.spec as NumericalControlSpec
                return Slider(spec.min.value, parameter.spec.max.value, control?.value ?: spec.defaultValue.value)
            }

            override fun createControl(detailInput: Slider, parameter: ParameterDef): KnobControl =
                KnobControl(parameter.name.text, detailInput.value)
        }

        object Envelope : ControlType<EnvelopeControl, HBox>() {
            override fun createDetailInput(
                parameter: ParameterDef,
                control: EnvelopeControl?,
                project: XenakisProject,
            ): HBox {
                val defaultColor = (parameter.spec as? NumericalControlSpec)?.associatedColor
                val colorPicker = ColorPicker(control?.displayColor ?: defaultColor ?: Color.BLACK)
                val toggle = ToggleSwitch()
                toggle.isSelected = control?.display ?: true
                val space = Region()
                setHgrow(space, Priority.ALWAYS)
                val box = HBox(colorPicker, space, toggle)
                box.alignment = Pos.CENTER_LEFT
                box.userData = control?.envelope
                return box
            }

            override fun createControl(detailInput: HBox, parameter: ParameterDef): EnvelopeControl {
                val colorPicker = detailInput.children[0] as ColorPicker
                val toggleButton = detailInput.children[2] as ToggleSwitch
                val spec = parameter.spec as NumericalControlSpec
                val oldEnvelope = detailInput.userData as? xenakis.sc.Envelope
                val env = oldEnvelope ?: xenakis.sc.Envelope.constant(spec.defaultValue.value, spec.warp)
                return EnvelopeControl(
                    parameter.name.text, env,
                    colorPicker.value, toggleButton.isSelected,
                )
            }
        }

        object Custom : ControlType<CustomControl, TextField>() {
            override fun createDetailInput(
                parameter: ParameterDef,
                control: CustomControl?,
                project: XenakisProject,
            ): TextField = TextField(control?.expr?.code.orEmpty())

            override fun createControl(detailInput: TextField, parameter: ParameterDef): CustomControl =
                CustomControl(parameter.name.text, RawScExpr(detailInput.text))
        }

        object Bus : ControlType<BusControl, BusRefEditorControl>() {
            override fun createDetailInput(
                parameter: ParameterDef,
                control: BusControl?,
                project: XenakisProject
            ): BusRefEditorControl = busSelector(project, control?.bus)

            override fun createControl(detailInput: BusRefEditorControl, parameter: ParameterDef): BusControl =
                BusControl(parameter.name.text, detailInput.editor.result.now)
        }

        object BusValue : ControlType<BusValueControl, BusRefEditorControl>() {
            override fun createDetailInput(
                parameter: ParameterDef,
                control: BusValueControl?,
                project: XenakisProject
            ): BusRefEditorControl = busSelector(project, control?.bus)

            override fun createControl(detailInput: BusRefEditorControl, parameter: ParameterDef): BusValueControl =
                BusValueControl(parameter.name.text, detailInput.editor.result.now)
        }

        object Buffer : ControlType<BufferControl, SimpleChoiceEditorControl<xenakis.sc.Buffer>>() {
            override fun createDetailInput(
                parameter: ParameterDef,
                control: BufferControl?,
                project: XenakisProject
            ): SimpleChoiceEditorControl<xenakis.sc.Buffer> {
                val editor = BufferRefEditor(project.context, control?.buffer ?: NoBuffer)
                return SimpleChoiceEditorControl(editor, createBundle())
            }

            override fun createControl(
                detailInput: SimpleChoiceEditorControl<xenakis.sc.Buffer>,
                parameter: ParameterDef
            ): BufferControl = BufferControl(parameter.name.text, detailInput.editor.result.now)
        }

        companion object {
            val all: List<ControlType<*, *>> = listOf(Constant, Knob, Custom, Envelope, BusValue)

            @Suppress("UNCHECKED_CAST")
            fun <O : ParameterControl> getType(option: O) = when (option) {
                is KnobControl -> Knob
                is ConstantControl -> Constant
                is CustomControl -> Custom
                is EnvelopeControl -> Envelope
                is BusControl -> Bus
                is BusValueControl -> BusValue
                is BufferControl -> Buffer
                else -> throw AssertionError()
            } as ControlType<O, *>

            private fun busSelector(project: XenakisProject, bus: xenakis.sc.Bus?): BusRefEditorControl {
                val editor = BusRefEditor(project.context, xenakis.sc.Bus.output)
                if (bus != null) editor.select(bus)
                return BusRefEditorControl(editor, createBundle())
            }
        }
    }
}