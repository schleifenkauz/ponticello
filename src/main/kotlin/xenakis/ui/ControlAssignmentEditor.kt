package xenakis.ui

import bundles.createBundle
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
import xenakis.sc.editor.BusSelector
import xenakis.sc.editor.SampleSelector
import xenakis.sc.view.ObjectSelectorControl

class ControlAssignmentEditor(
    private val obj: ScoreObject,
    val parameter: String,
    private val spec: ControlSpec,
    val project: XenakisProject
) : HBox(5.0) {
    private val nameLabel = Label(parameter).also { l -> l.styleClass.add("control-label") }
    private val comboBox = ComboBox(observableList(ControlType.all))
    private val detailEditors = mutableMapOf<ControlType<*, *>, Node>()
    private var detailEditor: Node? = null
        set(value) {
            field = value!!
            value.styleClass?.add("control-detail-editor")
            if (spec is NumericalControlSpec) {
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
            detailEditor = detailEditors.getOrPut(t) { t.createDetailInput(parameter, spec, null, project) }
        }
        nameLabel.minWidth = 150.0
        comboBox.prefWidth = COMBO_BOX_WIDTH
        comboBox.maxWidth = COMBO_BOX_WIDTH
        comboBox.minWidth = COMBO_BOX_WIDTH
    }

    fun setControl(control: ParameterControl) {
        val type = ControlType.getType(control)
        comboBox.value = type
        detailEditor = type.createDetailInput(parameter, spec, control, project)
        detailEditors[type] = detailEditor!!
    }

    fun getControl(): ParameterControl {
        @Suppress("UNCHECKED_CAST")
        val type = comboBox.value!! as ControlType<ParameterControl, Node>
        return type.createControl(obj, detailEditor!!, spec)
    }

    sealed class ControlType<C : ParameterControl, I : Node> {
        abstract fun createDetailInput(parameter: String, spec: ControlSpec, control: C?, project: XenakisProject): I

        abstract fun createControl(obj: ScoreObject, detailInput: I, spec: ControlSpec): C

        override fun toString(): String = this::class.simpleName!!

        object Constant : ControlType<ConstantControl, Spinner<Double>>() {
            override fun createDetailInput(
                parameter: String,
                spec: ControlSpec,
                control: ConstantControl?,
                project: XenakisProject,
            ): Spinner<Double> {
                spec as NumericalControlSpec
                return Spinner(
                    spec.min.get(), spec.max.get(),
                    control?.value ?: spec.defaultValue.get(), spec.step.get()
                )
            }

            override fun createControl(
                obj: ScoreObject,
                detailInput: Spinner<Double>,
                spec: ControlSpec
            ) = ConstantControl(detailInput.value)
        }

        object Knob : ControlType<KnobControl, Slider>() {
            override fun createDetailInput(
                parameter: String,
                spec: ControlSpec,
                control: KnobControl?,
                project: XenakisProject,
            ): Slider {
                spec as NumericalControlSpec
                val slider = Slider(spec.min.get(), spec.max.get(), control?.get() ?: spec.defaultValue.get())
                slider.blockIncrement = spec.step.get()
                slider.majorTickUnit = spec.step.get()
                slider.minorTickCount = 0
                slider.isSnapToTicks = true
                val accuracy = accuracy(spec.step.get())
                slider.tooltipProperty().bind(slider.valueProperty().map { value ->
                    val v = value.toDouble().format(accuracy)
                    Tooltip("${parameter}: $v")
                })
                return slider
            }

            override fun createControl(
                obj: ScoreObject,
                detailInput: Slider,
                spec: ControlSpec
            ): KnobControl = KnobControl(detailInput.value)
        }

        object Envelope : ControlType<EnvelopeControl, HBox>() {
            override fun createDetailInput(
                parameter: String,
                spec: ControlSpec,
                control: EnvelopeControl?,
                project: XenakisProject,
            ): HBox {
                val defaultColor = (spec as? NumericalControlSpec)?.associatedColor
                val colorPicker = ColorPicker(control?.displayColor ?: defaultColor ?: Color.WHITE)
                val toggle = ToggleSwitch()
                toggle.isSelected = control?.display ?: true
                val space = Region()
                setHgrow(space, Priority.ALWAYS)
                val box = HBox(colorPicker, space, toggle)
                box.alignment = Pos.CENTER_LEFT
                box.userData = control?.envelope
                return box
            }

            override fun createControl(
                obj: ScoreObject,
                detailInput: HBox,
                spec: ControlSpec
            ): EnvelopeControl {
                val colorPicker = detailInput.children[0] as ColorPicker
                val toggleButton = detailInput.children[2] as ToggleSwitch
                spec as NumericalControlSpec
                val oldEnvelope = detailInput.userData as? xenakis.model.Envelope
                val env = oldEnvelope
                    ?: xenakis.model.Envelope.constant(spec.defaultValue.get(), obj.duration, spec.warp)
                return EnvelopeControl(
                    env, colorPicker.value,
                    toggleButton.isSelected,
                )
            }
        }

        object LFO : ControlType<CustomControl, TextField>() {
            override fun createDetailInput(
                parameter: String,
                spec: ControlSpec,
                control: CustomControl?,
                project: XenakisProject,
            ): TextField = TextField(control?.expr?.code(project.context).orEmpty())

            override fun createControl(
                obj: ScoreObject,
                detailInput: TextField,
                spec: ControlSpec
            ): CustomControl = CustomControl(RawScExpr(detailInput.text))
        }

        object Bus : ControlType<BusControl, ObjectSelectorControl<BusObject, BusObjectReference>>() {
            override fun createDetailInput(
                parameter: String,
                spec: ControlSpec,
                control: BusControl?,
                project: XenakisProject
            ): ObjectSelectorControl<BusObject, BusObjectReference> = busSelector(project, control?.bus)

            override fun createControl(
                obj: ScoreObject,
                detailInput: ObjectSelectorControl<BusObject, BusObjectReference>,
                spec: ControlSpec
            ): BusControl = BusControl(detailInput.editor.result.now)
        }

        object BusValue : ControlType<BusValueControl, ObjectSelectorControl<BusObject, BusObjectReference>>() {
            override fun createDetailInput(
                parameter: String,
                spec: ControlSpec,
                control: BusValueControl?,
                project: XenakisProject
            ): ObjectSelectorControl<BusObject, BusObjectReference> = busSelector(project, control?.bus)

            override fun createControl(
                obj: ScoreObject,
                detailInput: ObjectSelectorControl<BusObject, BusObjectReference>,
                spec: ControlSpec
            ): BusValueControl = BusValueControl(detailInput.editor.result.now)
        }

        object SingleBusValue :
            ControlType<SingleBusValueControl, ObjectSelectorControl<BusObject, BusObjectReference>>() {
            override fun createDetailInput(
                parameter: String,
                spec: ControlSpec,
                control: SingleBusValueControl?,
                project: XenakisProject
            ): ObjectSelectorControl<BusObject, BusObjectReference> = busSelector(project, control?.bus)

            override fun createControl(
                obj: ScoreObject,
                detailInput: ObjectSelectorControl<BusObject, BusObjectReference>,
                spec: ControlSpec
            ): SingleBusValueControl = SingleBusValueControl(detailInput.editor.result.now)
        }

        object Buffer : ControlType<BufferControl, HBox>() {
            override fun createDetailInput(
                parameter: String,
                spec: ControlSpec,
                control: BufferControl?,
                project: XenakisProject
            ): HBox {
                val initialValue = control?.sample
                val editor = SampleSelector(project.context, initialValue)
                val selectorControl = ObjectSelectorControl(editor, createBundle())
                val displaySwitch = ToggleSwitch("Display")
                spec as BufferControlSpec
                val display = control?.display ?: spec.isPlayBufSource
                displaySwitch.isSelected = display
                return HBox(5.0, selectorControl, displaySwitch).centerChildrenVertically()
            }

            override fun createControl(obj: ScoreObject, detailInput: HBox, spec: ControlSpec): BufferControl {
                val sampleSelectorControl = detailInput.children[0] as ObjectSelectorControl<*, *>
                val sampleSelector = sampleSelectorControl.editor as SampleSelector
                val displaySwitch = detailInput.children[1] as ToggleSwitch
                return BufferControl(sampleSelector.result.now, displaySwitch.isSelected)
            }
        }

        companion object {
            val all: List<ControlType<*, *>> = listOf(Constant, Knob, LFO, Envelope, BusValue, SingleBusValue)

            @Suppress("UNCHECKED_CAST")
            fun <O : ParameterControl> getType(option: O) = when (option) {
                is KnobControl -> Knob
                is ConstantControl -> Constant
                is CustomControl -> LFO
                is EnvelopeControl -> Envelope
                is BusControl -> Bus
                is BusValueControl -> BusValue
                is SingleBusValueControl -> SingleBusValue
                is BufferControl -> Buffer
                else -> throw AssertionError()
            } as ControlType<O, *>

            private fun busSelector(
                project: XenakisProject,
                bus: BusObjectReference?
            ): ObjectSelectorControl<BusObject, BusObjectReference> {
                val editor =
                    BusSelector(project.context, initialValue = project.busses.getDefault().createReference())
                if (bus != null) editor.select(bus)
                return ObjectSelectorControl(editor, createBundle())
            }
        }
    }

    companion object {
        private const val COMBO_BOX_WIDTH = 120.0
    }
}