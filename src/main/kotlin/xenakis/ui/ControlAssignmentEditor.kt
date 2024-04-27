package xenakis.ui

import javafx.collections.FXCollections.observableList
import javafx.geometry.Pos
import javafx.scene.Node
import javafx.scene.control.*
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.Region
import org.controlsfx.control.ToggleSwitch
import xenakis.model.*
import xenakis.sc.*

class ControlAssignmentEditor(private val parameter: ParameterDef, val project: XenakisProject) : HBox(5.0) {
    private val nameLabel = Label(parameter.name).also { l -> l.styleClass.add("control-label") }
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
                return Spinner(spec.min, spec.max, control?.value ?: spec.default)
            }

            override fun createControl(detailInput: Spinner<Double>, parameter: ParameterDef) =
                ConstantControl(parameter.name, detailInput.value)
        }

        object Knob : ControlType<KnobControl, Slider>() {
            override fun createDetailInput(
                parameter: ParameterDef,
                control: KnobControl?,
                project: XenakisProject,
            ): Slider {
                val spec = parameter.spec as NumericalControlSpec
                return Slider(spec.min, parameter.spec.max, control?.value ?: spec.default)
            }

            override fun createControl(detailInput: Slider, parameter: ParameterDef): KnobControl =
                KnobControl(parameter.name, detailInput.value)
        }

        object Envelope : ControlType<EnvelopeControl, HBox>() {
            override fun createDetailInput(
                parameter: ParameterDef,
                control: EnvelopeControl?,
                project: XenakisProject,
            ): HBox {
                val colorPicker = ColorPicker(control?.displayColor ?: parameter.associatedColor)
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
                val env = oldEnvelope ?: xenakis.sc.Envelope.constant(spec.default, spec.warp)
                return EnvelopeControl(
                    parameter.name, env, parameter.spec,
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
                CustomControl(parameter.name, RawScExpr(detailInput.text))
        }

        object Bus : ControlType<BusControl, ComboBox<xenakis.sc.Bus>>() {
            override fun createDetailInput(
                parameter: ParameterDef,
                control: BusControl?,
                project: XenakisProject
            ): ComboBox<xenakis.sc.Bus> = ComboBox(observableList(project.flowGraph.busses.map { it.bus }))

            override fun createControl(
                detailInput: ComboBox<xenakis.sc.Bus>,
                parameter: ParameterDef
            ): BusControl = BusControl(parameter.name, detailInput.value)
        }

        object Buffer : ControlType<BufferControl, ComboBox<xenakis.sc.Buffer>>() {
            override fun createDetailInput(
                parameter: ParameterDef,
                control: BufferControl?,
                project: XenakisProject
            ): ComboBox<xenakis.sc.Buffer> = ComboBox(observableList(project.buffers))

            override fun createControl(
                detailInput: ComboBox<xenakis.sc.Buffer>,
                parameter: ParameterDef
            ): BufferControl = BufferControl(parameter.name, detailInput.value)
        }

        companion object {
            val all: List<ControlType<*, *>> = listOf(Constant, Knob, Custom, Envelope)

            @Suppress("UNCHECKED_CAST")
            fun <O : ParameterControl> getType(option: O) = when (option) {
                is KnobControl -> Knob
                is ConstantControl -> Constant
                is CustomControl -> Custom
                is EnvelopeControl -> Envelope
                else -> throw AssertionError()
            } as ControlType<O, *>
        }
    }
}