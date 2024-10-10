package xenakis.ui

import bundles.createBundle
import hextant.context.Context
import hextant.fx.initHextantScene
import hextant.serial.EditorRoot
import javafx.geometry.Pos
import javafx.scene.Node
import javafx.scene.control.Button
import javafx.scene.control.Label
import javafx.scene.control.ScrollPane
import javafx.scene.layout.BorderPane
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import org.controlsfx.control.ToggleSwitch
import reaktive.value.ReactiveVariable
import reaktive.value.fx.asProperty
import reaktive.value.now
import reaktive.value.reactiveVariable
import xenakis.impl.Decimal
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
) : HBox(5.0) {
    private val nameLabel = Label(parameter)
    private var selectedOption: ControlType<*>? = null
    private val optionButton = Button() styleClass "sleek-button"
    private val detailEditors = mutableMapOf<ControlType<*>, Node>()
    private val spec
        get() = obj.getSpec(parameter) ?: error("Parameter $parameter not found in $obj")
    private val deleteBtn = Icon.Delete.button(radius = 12.0, action = "Remove control") {
        obj.controls.removeControl(parameter)
    }
    private var settingControl = false
    private var detailEditor: Node? = null
        set(value) {
            field = value!!
            value.styleClass?.add("control-detail-editor")
            if (spec is NumericalControlSpec) {
                children.setAll(nameLabel, optionButton, detailEditor, infiniteSpace(), deleteBtn)
            } else {
                children.setAll(nameLabel, detailEditor, infiniteSpace(), deleteBtn)
            }
            setHgrow(value, Priority.ALWAYS)
        }

    init {
        styleClass.add("detail-item")
        optionButton.setOnMouseClicked { ev ->
            if (ev.isShiftDown) ControlSpecPrompt(obj, parameter, spec).showDialog(obj.context, optionButton)
            else {
                val listView = SimpleSearchableListView(ControlType.all, "Select control type")
                listView.showPopup(
                    obj.context, anchorNode = optionButton,
                    initialOption = selectedOption
                ) { option ->
                    updateControlType(option)
                }
            }
        }
        optionButton.minWidth = 85.0
        nameLabel.minWidth = DetailPane.LABEL_WIDTH - 5.0
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
        optionButton.text = type.toString()
        detailEditor = type.createDetailInput(parameter, spec, control, obj.context)
        detailEditors[type] = detailEditor!!
        settingControl = false
    }

    fun focusInputControl(): Boolean {
        detailEditor?.requestFocus()
        return detailEditor != null
    }

    sealed class ControlType<C : ParameterControl> {
        abstract fun createDetailInput(parameter: String, spec: ControlSpec, control: C, context: Context): Node

        abstract fun createDefaultControl(obj: ScoreObject, spec: ControlSpec, initialValue: Decimal?): C

        override fun toString(): String = when (this) {
            Buffer -> "Buffer"
            Bus -> "Bus"
            BusValue -> "Bus"
            Value -> "Value"
            Envelope -> "Envelope"
            Group -> "Group"
            LFO -> "LFO"
            SingleBusValue -> "Bus Value"
        }

        object Value : ControlType<ConstantControl>() {
            override fun createDetailInput(
                parameter: String,
                spec: ControlSpec,
                control: ConstantControl,
                context: Context
            ): Node = ControlSlider(control.value, spec as NumericalControlSpec)

            override fun createDefaultControl(
                obj: ScoreObject, spec: ControlSpec, initialValue: Decimal?
            ): ConstantControl {
                spec as NumericalControlSpec
                return ConstantControl(reactiveVariable(initialValue ?: spec.defaultValue.get()))
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
                initialValue: Decimal?
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
                window.resize(500.0, 200.0)
                return button("Code") { window.show() }
            }

            override fun createDefaultControl(
                obj: ScoreObject,
                spec: ControlSpec,
                initialValue: Decimal?
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

            override fun createDefaultControl(obj: ScoreObject, spec: ControlSpec, initialValue: Decimal?): BusControl =
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
                initialValue: Decimal?
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
                obj: ScoreObject, spec: ControlSpec, initialValue: Decimal?
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
                return HBox(selectorControl, infiniteSpace(), displaySwitch).centerChildren()
            }

            override fun createDefaultControl(
                obj: ScoreObject,
                spec: ControlSpec,
                initialValue: Decimal?
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
                initialValue: Decimal?
            ): GroupControl =
                GroupControl(reactiveVariable(obj.context[GroupRegistry].getDefault().createReference()))
        }

        companion object {
            val all: List<ControlType<*>> = listOf(Value, LFO, Envelope, BusValue, SingleBusValue)

            @Suppress("UNCHECKED_CAST")
            fun <O : ParameterControl> getType(option: O) = when (option) {
                is ConstantControl -> Value
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
}