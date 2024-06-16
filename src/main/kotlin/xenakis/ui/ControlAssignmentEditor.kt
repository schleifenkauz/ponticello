package xenakis.ui

import bundles.createBundle
import hextant.context.Context
import hextant.serial.EditorRoot
import javafx.collections.FXCollections.observableList
import javafx.geometry.Pos
import javafx.scene.Node
import javafx.scene.control.*
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.Region
import org.controlsfx.control.ToggleSwitch
import reaktive.value.ReactiveVariable
import reaktive.value.fx.asProperty
import reaktive.value.now
import reaktive.value.reactiveVariable
import xenakis.model.*
import xenakis.sc.BufferControlSpec
import xenakis.sc.ControlSpec
import xenakis.sc.NumericalControlSpec
import xenakis.sc.editor.BusSelector
import xenakis.sc.editor.GroupSelector
import xenakis.sc.editor.SampleSelector
import xenakis.sc.editor.ScExprExpander
import xenakis.sc.view.ObjectSelectorControl

class ControlAssignmentEditor(
    private val obj: SynthObject,
    val parameter: String,
    private val spec: ControlSpec
) : HBox(5.0) {
    private val nameLabel = Label(parameter).also { l -> l.styleClass.add("control-label") }
    private val comboBox = ComboBox(observableList(ControlType.all))
    private val detailEditors = mutableMapOf<ControlType<*>, Node>()
    private var settingControl = false
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
        styleClass.add("detail-item")
        comboBox.styleClass.add("control-option-selector")
        comboBox.valueProperty().addListener { _, _, t ->
            if (settingControl) return@addListener
            @Suppress("UNCHECKED_CAST")
            t as ControlType<ParameterControl>
            val control = t.createDefaultControl(obj, spec, obj.context)
            obj.controls.reassignControl(parameter, control)
        }
        nameLabel.minWidth = DetailPane.LABEL_WIDTH - 5.0
        comboBox.setFixedWidth(COMBO_BOX_WIDTH)
    }

    fun setControl(control: ParameterControl) {
        val type = ControlType.getType(control)
        settingControl = true
        comboBox.value = type
        detailEditor = type.createDetailInput(spec, control, obj.context)
        detailEditors[type] = detailEditor!!
        settingControl = false
    }

    sealed class ControlType<C : ParameterControl> {
        abstract fun createDetailInput(spec: ControlSpec, control: C, context: Context): Node

        abstract fun createDefaultControl(obj: ScoreObject, spec: ControlSpec, context: Context): C

        override fun toString(): String = this::class.simpleName!!

        object Constant : ControlType<ConstantControl>() {
            override fun createDetailInput(
                spec: ControlSpec,
                control: ConstantControl,
                context: Context,
            ): Spinner<Double> {
                spec as NumericalControlSpec
                val spinner = Spinner<Double>(
                    spec.min.get(), spec.max.get(),
                    control.value.now,
                    spec.step.get()
                )
                spinner.isEditable = true
                spinner.valueFactory.valueProperty().bindBidirectional(control.value.asProperty())
                return spinner
            }

            override fun createDefaultControl(obj: ScoreObject, spec: ControlSpec, context: Context): ConstantControl {
                spec as NumericalControlSpec
                return ConstantControl(reactiveVariable(spec.defaultValue.get()))
            }
        }

        object Knob : ControlType<KnobControl>() {
            override fun createDetailInput(spec: ControlSpec, control: KnobControl, context: Context): Node {
                spec as NumericalControlSpec
                val slider = Slider(spec.min.get(), spec.max.get(), control.get())
                slider.blockIncrement = spec.step.get()
                slider.majorTickUnit = spec.step.get()
                slider.minorTickCount = 0
                slider.isSnapToTicks = true
                val accuracy = accuracy(spec.step.get())
                slider.tooltipProperty().bind(slider.valueProperty().map { value ->
                    val v = value.toDouble().format(accuracy)
                    Tooltip(v)
                })
                return slider
            }

            override fun createDefaultControl(obj: ScoreObject, spec: ControlSpec, context: Context): KnobControl {
                spec as NumericalControlSpec
                return KnobControl(spec.defaultValue.get())
            }
        }

        object Envelope : ControlType<EnvelopeControl>() {
            override fun createDetailInput(spec: ControlSpec, control: EnvelopeControl, context: Context): Node {
                val colorPicker = colorPicker(control.displayColor)
                val toggle = ToggleSwitch()
                toggle.selectedProperty().bindBidirectional(control.display.asProperty())
                val space = Region()
                setHgrow(space, Priority.ALWAYS)
                val box = HBox(colorPicker, space, toggle)
                box.alignment = Pos.CENTER_LEFT
                return box
            }

            override fun createDefaultControl(obj: ScoreObject, spec: ControlSpec, context: Context): EnvelopeControl {
                spec as NumericalControlSpec
                val env = xenakis.model.Envelope.constant(spec.defaultValue.get(), obj.duration, spec.warp)
                val displayColor = reactiveVariable(spec.associatedColor)
                val display = reactiveVariable(true)
                return EnvelopeControl(env, displayColor, display)
            }
        }

        object LFO : ControlType<CustomControl>() {
            override fun createDetailInput(spec: ControlSpec, control: CustomControl, context: Context): Node =
                control.expr.control

            override fun createDefaultControl(obj: ScoreObject, spec: ControlSpec, context: Context): CustomControl {
                val editor = ScExprExpander(context)
                val root = EditorRoot.create(editor)
                return CustomControl(root)
            }
        }

        object Bus : ControlType<BusControl>() {
            override fun createDetailInput(spec: ControlSpec, control: BusControl, context: Context): Node =
                busSelector(context, control.bus)

            override fun createDefaultControl(obj: ScoreObject, spec: ControlSpec, context: Context): BusControl =
                BusControl(reactiveVariable(context[BusRegistry].getDefault().createReference()))
        }

        object BusValue : ControlType<BusValueControl>() {
            override fun createDetailInput(spec: ControlSpec, control: BusValueControl, context: Context): Node =
                busSelector(context, control.bus)

            override fun createDefaultControl(obj: ScoreObject, spec: ControlSpec, context: Context): BusValueControl =
                BusValueControl(reactiveVariable(context[BusRegistry].getDefault().createReference()))
        }

        object SingleBusValue :
            ControlType<SingleBusValueControl>() {
            override fun createDetailInput(spec: ControlSpec, control: SingleBusValueControl, context: Context): Node =
                busSelector(context, control.bus)

            override fun createDefaultControl(
                obj: ScoreObject, spec: ControlSpec, context: Context
            ): SingleBusValueControl =
                SingleBusValueControl(reactiveVariable(context[BusRegistry].getDefault().createReference()))

        }

        object Buffer : ControlType<BufferControl>() {
            override fun createDetailInput(spec: ControlSpec, control: BufferControl, context: Context): Node {
                val initialValue = control.sample
                val editor = SampleSelector(context, initialValue)
                val selectorControl = ObjectSelectorControl(editor, createBundle())
                val displaySwitch = ToggleSwitch("Display")
                spec as BufferControlSpec
                displaySwitch.selectedProperty().bindBidirectional(control.display.asProperty())
                return HBox(5.0, selectorControl, displaySwitch).centerChildrenVertically()
            }

            override fun createDefaultControl(obj: ScoreObject, spec: ControlSpec, context: Context): BufferControl {
                spec as BufferControlSpec
                val display = reactiveVariable(spec.isPlayBufSource)
                val sample: ReactiveVariable<SampleObjectReference?> = reactiveVariable(null)
                return BufferControl(sample, display)
            }
        }

        object Group : ControlType<GroupControl>() {
            override fun createDetailInput(spec: ControlSpec, control: GroupControl, context: Context): Node {
                val selector = GroupSelector(context, control.group)
                return ObjectSelectorControl(selector, createBundle())
            }

            override fun createDefaultControl(obj: ScoreObject, spec: ControlSpec, context: Context): GroupControl =
                GroupControl(reactiveVariable(context[GroupRegistry].getDefault().createReference()))
        }

        companion object {
            val all: List<ControlType<*>> = listOf(Constant, Knob, LFO, Envelope, BusValue, SingleBusValue)

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
                is GroupControl -> Group
                else -> throw AssertionError()
            } as ControlType<O>

            private fun busSelector(
                context: Context,
                bus: ReactiveVariable<BusObjectReference>
            ): ObjectSelectorControl<BusObject, BusObjectReference> {
                val editor = BusSelector(context, selected = bus)
                return ObjectSelectorControl(editor, createBundle())
            }
        }
    }

    companion object {
        private const val COMBO_BOX_WIDTH = 120.0
    }
}