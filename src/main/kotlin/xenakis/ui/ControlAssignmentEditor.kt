package xenakis.ui

import bundles.createBundle
import hextant.context.Context
import hextant.fx.initHextantScene
import hextant.serial.EditorRoot
import javafx.collections.FXCollections.observableList
import javafx.geometry.Pos
import javafx.scene.Node
import javafx.scene.control.*
import javafx.scene.layout.BorderPane
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.util.Duration
import org.controlsfx.control.ToggleSwitch
import reaktive.value.ReactiveVariable
import reaktive.value.forEach
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
            updateControlType(t)
        }
        nameLabel.minWidth = DetailPane.LABEL_WIDTH - 5.0
        comboBox.setFixedWidth(COMBO_BOX_WIDTH)
    }

    private fun updateControlType(t: ControlType<*>) {
        val oldValue = when (val oldControl = obj.controls[parameter]) {
            is ConstantControl -> oldControl.value.now
            is KnobControl -> oldControl.get()
            else -> null
        }
        val control = t.createDefaultControl(obj, spec, oldValue)
        obj.controls.reassignControl(parameter, control)
    }

    fun setControl(control: ParameterControl) {
        val type = ControlType.getType(control)
        settingControl = true
        comboBox.value = type
        detailEditor = type.createDetailInput(parameter, spec, control, obj.context)
        detailEditors[type] = detailEditor!!
        settingControl = false
    }

    fun getInputControl() = detailEditor

    fun focusInputControl(): Boolean {
        detailEditor?.requestFocus()
        return detailEditor != null
    }

    sealed class ControlType<C : ParameterControl> {
        abstract fun createDetailInput(parameter: String, spec: ControlSpec, control: C, context: Context): Node

        abstract fun createDefaultControl(obj: ScoreObject, spec: ControlSpec, initialValue: Double?): C

        override fun toString(): String = this::class.simpleName!!

        object Constant : ControlType<ConstantControl>() {
            override fun createDetailInput(
                parameter: String,
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

            override fun createDefaultControl(
                obj: ScoreObject,
                spec: ControlSpec,
                initialValue: Double?
            ): ConstantControl {
                spec as NumericalControlSpec
                return ConstantControl(reactiveVariable(initialValue ?: spec.defaultValue.get()))
            }
        }

        object Knob : ControlType<KnobControl>() {
            override fun createDetailInput(
                parameter: String,
                spec: ControlSpec,
                control: KnobControl,
                context: Context
            ): Node {
                spec as NumericalControlSpec
                val min = spec.min.get()
                val max = spec.max.get()
                val step = spec.step.get()
                val slider = Slider(min, max, control.get())
                slider.isShowTickMarks = (max - min) / step < 25.0
                slider.blockIncrement = step
                slider.majorTickUnit = step
                slider.minorTickCount = 0
                slider.isSnapToTicks = true
                val accuracy = accuracy(step)
                slider.valueProperty().addListener { _, _, value -> control.value.set(value.toDouble()) }
                slider.tooltip = Tooltip().apply {
                    hideDelay = Duration.ONE
                    showDelay = Duration.ZERO
                }
                slider.userData = control.value.forEach { value ->
                    slider.value = value
                    slider.tooltip.text = value.format(accuracy)
                }
                return slider
            }

            override fun createDefaultControl(obj: ScoreObject, spec: ControlSpec, initialValue: Double?): KnobControl {
                spec as NumericalControlSpec
                return KnobControl(reactiveVariable(initialValue ?: spec.defaultValue.get()))
            }
        }

        object Envelope : ControlType<EnvelopeControl>() {
            override fun createDetailInput(
                parameter: String,
                spec: ControlSpec,
                control: EnvelopeControl,
                context: Context
            ): Node {
                val colorPicker = colorPicker(control.displayColor)
                colorPicker.setFixedWidth(30.0)
                val toggle = ToggleSwitch("Display: ")
                toggle.selectedProperty().bindBidirectional(control.display.asProperty())
                val box = HBox(colorPicker, infiniteSpace(), toggle)
                box.alignment = Pos.CENTER_LEFT
                return box
            }

            override fun createDefaultControl(
                obj: ScoreObject,
                spec: ControlSpec,
                initialValue: Double?
            ): EnvelopeControl {
                spec as NumericalControlSpec
                val value = initialValue ?: spec.defaultValue.get()
                val env = xenakis.model.Envelope.constant(value, obj.duration, spec.warp)
                val displayColor = reactiveVariable(spec.associatedColor)
                val display = reactiveVariable(true)
                return EnvelopeControl(env, displayColor, display)
            }
        }

        object LFO : ControlType<CustomControl>() {
            override fun createDetailInput(
                parameter: String,
                spec: ControlSpec,
                control: CustomControl,
                context: Context
            ): Node {
                val pane = ScrollPane(control.expr.control)
                val window = SubWindow(BorderPane(pane), "LFO for $parameter", context)
                window.scene.initHextantScene(context)
                window.resize(800.0, 800.0)
                return button("Code") { window.show() }
            }

            override fun createDefaultControl(
                obj: ScoreObject,
                spec: ControlSpec,
                initialValue: Double?
            ): CustomControl {
                val editor = ScExprExpander(obj.context)
                val root = EditorRoot.create(editor)
                return CustomControl(root)
            }
        }

        object Bus : ControlType<BusControl>() {
            override fun createDetailInput(
                parameter: String,
                spec: ControlSpec,
                control: BusControl,
                context: Context
            ): Node =
                busSelector(context, control.bus)

            override fun createDefaultControl(obj: ScoreObject, spec: ControlSpec, initialValue: Double?): BusControl =
                BusControl(reactiveVariable(obj.context[BusRegistry].getDefault().createReference()))
        }

        object BusValue : ControlType<BusValueControl>() {
            override fun createDetailInput(
                parameter: String,
                spec: ControlSpec,
                control: BusValueControl,
                context: Context
            ): Node =
                busSelector(context, control.bus)

            override fun createDefaultControl(
                obj: ScoreObject,
                spec: ControlSpec,
                initialValue: Double?
            ): BusValueControl =
                BusValueControl(reactiveVariable(obj.context[BusRegistry].getDefault().createReference()))
        }

        object SingleBusValue :
            ControlType<SingleBusValueControl>() {
            override fun createDetailInput(
                parameter: String,
                spec: ControlSpec,
                control: SingleBusValueControl,
                context: Context
            ): Node =
                busSelector(context, control.bus)

            override fun createDefaultControl(
                obj: ScoreObject, spec: ControlSpec, initialValue: Double?
            ): SingleBusValueControl =
                SingleBusValueControl(reactiveVariable(obj.context[BusRegistry].getDefault().createReference()))

        }

        object Buffer : ControlType<BufferControl>() {
            override fun createDetailInput(
                parameter: String,
                spec: ControlSpec,
                control: BufferControl,
                context: Context
            ): Node {
                val initialValue = control.sample
                val editor = SampleSelector(context, initialValue)
                val selectorControl = ObjectSelectorControl(editor, createBundle())
                val displaySwitch = ToggleSwitch("Display: ")
                spec as BufferControlSpec
                displaySwitch.selectedProperty().bindBidirectional(control.display.asProperty())
                return HBox(selectorControl, infiniteSpace(), displaySwitch).centerChildrenVertically()
            }

            override fun createDefaultControl(
                obj: ScoreObject,
                spec: ControlSpec,
                initialValue: Double?
            ): BufferControl {
                spec as BufferControlSpec
                val display = reactiveVariable(spec.isPlayBufSource)
                val sample: ReactiveVariable<ObjectReference?> = reactiveVariable(null)
                return BufferControl(sample, display)
            }
        }

        object Group : ControlType<GroupControl>() {
            override fun createDetailInput(
                parameter: String,
                spec: ControlSpec,
                control: GroupControl,
                context: Context
            ): Node {
                val selector = GroupSelector(context, control.group)
                return ObjectSelectorControl(selector, createBundle())
            }

            override fun createDefaultControl(
                obj: ScoreObject,
                spec: ControlSpec,
                initialValue: Double?
            ): GroupControl =
                GroupControl(reactiveVariable(obj.context[GroupRegistry].getDefault().createReference()))
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
                ref: ReactiveVariable<ObjectReference>
            ): ObjectSelectorControl<BusObject, ObjectReference> {
                val bus = ref.now.get<BusObject>()
                val editor = BusSelector(context, bus.rate.now, bus.channels.now, ref)
                return ObjectSelectorControl(editor, createBundle())
            }
        }
    }

    companion object {
        private const val COMBO_BOX_WIDTH = 120.0
    }
}