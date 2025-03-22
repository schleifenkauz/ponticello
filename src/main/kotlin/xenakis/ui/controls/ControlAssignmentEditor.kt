package xenakis.ui.controls

import bundles.createBundle
import fxutils.*
import fxutils.controls.SliderBar
import fxutils.prompt.SimpleSearchableListView
import hextant.context.Context
import hextant.fx.initHextantScene
import hextant.serial.EditorRoot
import javafx.geometry.Pos
import javafx.scene.Node
import javafx.scene.control.Button
import javafx.scene.control.ScrollPane
import javafx.scene.input.DragEvent
import javafx.scene.input.Dragboard
import javafx.scene.layout.BorderPane
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import org.controlsfx.control.ToggleSwitch
import reaktive.value.ReactiveVariable
import reaktive.value.fx.asProperty
import reaktive.value.now
import reaktive.value.reactiveValue
import reaktive.value.reactiveVariable
import xenakis.impl.asTime
import xenakis.model.obj.*
import xenakis.model.registry.*
import xenakis.model.score.*
import xenakis.model.score.ParameterControls.NamedParameterControl
import xenakis.sc.*
import xenakis.sc.editor.BusSelector
import xenakis.sc.editor.GroupSelector
import xenakis.sc.editor.SampleSelector
import xenakis.sc.editor.ScExprExpander
import xenakis.sc.view.ObjectSelectorControl
import xenakis.ui.impl.colorPicker

class ControlAssignmentEditor(val control: NamedParameterControl) : HBox() {
    private var selectedOption: ControlType<*>? = null
    private val optionButton = Button() styleClass "sleek-button"
    private val detailEditors = mutableMapOf<ControlType<*>, Node>()
    private val spec get() = control.spec.now
    private var settingControl = false
    private var detailEditor: Node? = null
        set(value) {
            field = value!!
            children.clear()
            setHgrow(detailEditor, Priority.ALWAYS)
            if (spec is NumericalControlSpec) children.add(optionButton)
            children.add(detailEditor)
        }

    init {
        optionButton.isFocusTraversable = false
        optionButton.setOnMouseClicked { showOptionPopup() }
        optionButton.minWidth = 85.0
        setupDropArea(this::canDrop, ::onDrop)
        styleClass("control-detail-editor")
    }

    private fun onDrop(ev: DragEvent) {
        val db = ev.dragboard
        val samples = control.context[SampleRegistry]
        val sample =
            if (db.hasFile("wav")) samples.getOrAdd(db.files[0])
            else if (db.hasContent(SampleObject.DATA_FORMAT)) samples.get(db.getContent(SampleObject.DATA_FORMAT) as String)
            else return
        val ctrl = control.now as BufferControl
        ctrl.sample.set(sample.reference())
    }

    private fun canDrop(db: Dragboard): Boolean {
        if (control.now !is BufferControl) return false
        return db.hasFile("wav") || db.hasContent(SampleObject.DATA_FORMAT)
    }

    private fun showOptionPopup() {
        val listView = SimpleSearchableListView(ControlType.all, "Select control type")
        listView.showPopup(
            anchorNode = optionButton,
            initialOption = selectedOption
        ) { option ->
            updateControlType(option)
        }
    }

    private fun updateControlType(t: ControlType<*>) {
        selectedOption = t
        val oldControl = control.now
        val newControl = t.createDefaultControl(control.parentObject, spec, oldControl)
        control.reassign(newControl)
    }

    fun setControl(newControl: ParameterControl) {
        val type = ControlType.getType(newControl)
        settingControl = true
        optionButton.text = type.toString()
        detailEditor = type.createDetailInput(control.parentObject, control.name.now, spec, newControl)
        detailEditors[type] = detailEditor!!
        settingControl = false
    }

    fun focusInputControl(): Boolean {
        detailEditor?.requestFocus()
        return detailEditor != null
    }

    sealed class ControlType<C : ParameterControl> {
        abstract fun createDetailInput(
            obj: ParameterizedObject,
            parameter: String,
            spec: ControlSpec?,
            control: C
        ): Node

        abstract fun createDefaultControl(obj: ParameterizedObject, spec: ControlSpec?, oldControl: ParameterControl): C

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
                obj: ParameterizedObject,
                parameter: String, //TODO make parameter reactive
                spec: ControlSpec?,
                control: ConstantControl
            ): Node {
                val converter = (spec as NumericalControlSpec).converter()
                val sliderBar = SliderBar(
                    control.value, reactiveValue(parameter), converter,
                    style = SliderBar.Style.AlwaysValue
                )
                sliderBar.prefWidth = 150.0
                return sliderBar
            }

            override fun createDefaultControl(
                obj: ParameterizedObject, spec: ControlSpec?, oldControl: ParameterControl
            ): ConstantControl {
                spec as NumericalControlSpec
                return ConstantControl(reactiveVariable(oldControl.getNumericalValue() ?: spec.defaultValue.get()))
            }
        }

        object Envelope : ControlType<EnvelopeControl>() {
            override fun createDetailInput(
                obj: ParameterizedObject,
                parameter: String,
                spec: ControlSpec?,
                control: EnvelopeControl
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
                obj: ParameterizedObject,
                spec: ControlSpec?,
                oldControl: ParameterControl
            ): EnvelopeControl {
                spec as NumericalControlSpec
                val value = oldControl.getNumericalValue() ?: spec.defaultValue.get()
                val duration = (obj as? ScoreObject)?.duration ?: 1.0.asTime
                val env = xenakis.model.score.Envelope.constant(value, duration, spec.warp)
                val displayColor = reactiveVariable(spec.associatedColor)
                val display = reactiveVariable(true)
                return EnvelopeControl(env, displayColor, display)
            }
        }

        object LFO : ControlType<CustomControl>() {
            override fun createDetailInput(
                obj: ParameterizedObject,
                parameter: String,
                spec: ControlSpec?,
                control: CustomControl
            ): Node {
                val pane = ScrollPane(control.expr.control)
                val window = SubWindow(BorderPane(pane), "LFO for $parameter")
                window.scene.initHextantScene(obj.context)
                window.resize(500.0, 200.0)
                return button("Code") { window.show() }
            }

            override fun createDefaultControl(
                obj: ParameterizedObject,
                spec: ControlSpec?,
                oldControl: ParameterControl
            ): CustomControl {
                val editor = ScExprExpander()
                val root = EditorRoot(editor)
                if (oldControl.getNumericalValue() != null) {
                    editor.setInitialText(oldControl.getNumericalValue().toString())
                } else editor.setInitialText("")
                return CustomControl(root)
            }
        }

        object Bus : ControlType<BusControl>() {
            override fun createDetailInput(
                obj: ParameterizedObject,
                parameter: String,
                spec: ControlSpec?,
                control: BusControl
            ): Node = busSelector(control.bus, spec, obj.context)

            override fun createDefaultControl(
                obj: ParameterizedObject,
                spec: ControlSpec?,
                oldControl: ParameterControl
            ): BusControl {
                val initial = oldControl.getBus() ?: obj.context[BusRegistry].getDefault().reference()
                return BusControl(reactiveVariable(initial))
            }
        }

        object BusValue : ControlType<BusValueControl>() {
            override fun createDetailInput(
                obj: ParameterizedObject,
                parameter: String,
                spec: ControlSpec?,
                control: BusValueControl
            ): Node = busSelector(control.bus, spec, obj.context)

            override fun createDefaultControl(
                obj: ParameterizedObject,
                spec: ControlSpec?,
                oldControl: ParameterControl
            ): BusValueControl {
                val initial = oldControl.getBus() ?: obj.context[BusRegistry].getDefault().reference()
                return BusValueControl(reactiveVariable(initial))
            }
        }

        object SingleBusValue :
            ControlType<SingleBusValueControl>() {
            override fun createDetailInput(
                obj: ParameterizedObject,
                parameter: String,
                spec: ControlSpec?,
                control: SingleBusValueControl
            ): Node = busSelector(control.bus, spec, obj.context)

            override fun createDefaultControl(
                obj: ParameterizedObject, spec: ControlSpec?, oldControl: ParameterControl
            ): SingleBusValueControl {
                val initial = oldControl.getBus() ?: obj.context[BusRegistry].getDefault().reference()
                return SingleBusValueControl(reactiveVariable(initial))
            }

        }

        object Buffer : ControlType<BufferControl>() {
            override fun createDetailInput(
                obj: ParameterizedObject,
                parameter: String,
                spec: ControlSpec?,
                control: BufferControl
            ): Node {
                val editor = SampleSelector()
                spec as BufferControlSpec
                editor.setFilter(spec.channels)
                editor.syncWith(control.sample)
                editor.initialize(obj.context)
                val selectorControl = ObjectSelectorControl(editor, createBundle())
                val displaySwitch = ToggleSwitch("Display: ")
                displaySwitch.selectedProperty().bindBidirectional(control.display.asProperty())
                return HBox(selectorControl, infiniteSpace(), displaySwitch).centerChildren()
            }

            override fun createDefaultControl(
                obj: ParameterizedObject,
                spec: ControlSpec?,
                oldControl: ParameterControl
            ): BufferControl {
                spec as BufferControlSpec
                val display = reactiveVariable(spec.isPlayBufSource)
                val sample: ReactiveVariable<SampleReference> = reactiveVariable(ObjectReference.none())
                return BufferControl(sample, display)
            }
        }

        object Group : ControlType<GroupControl>() {
            override fun createDetailInput(
                obj: ParameterizedObject,
                parameter: String,
                spec: ControlSpec?,
                control: GroupControl
            ): Node {
                val selector = GroupSelector()
                selector.syncWith(control.group)
                selector.initialize(obj.context)
                return ObjectSelectorControl(selector, createBundle())
            }

            override fun createDefaultControl(
                obj: ParameterizedObject,
                spec: ControlSpec?,
                oldControl: ParameterControl
            ): GroupControl =
                GroupControl(reactiveVariable(obj.context[GroupRegistry].getDefault().reference()))
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
                control: ReactiveVariable<BusReference>,
                spec: ControlSpec?, context: Context
            ): ObjectSelectorControl<BusObject> {
                val editor = BusSelector()
                if (spec is BusControlSpec) editor.setFilter(spec.rate, spec.channels)
                else editor.setFilter(rate = Rate.Control, channels = 1)
                editor.syncWith(control)
                editor.initialize(context)
                return ObjectSelectorControl(editor, createBundle())
            }
        }
    }
}