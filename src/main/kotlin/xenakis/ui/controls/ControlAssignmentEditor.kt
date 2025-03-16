package xenakis.ui.controls

import bundles.createBundle
import fxutils.*
import fxutils.actions.ActionBar
import fxutils.actions.collectActions
import fxutils.controls.SliderBar
import fxutils.prompt.DetailPane
import fxutils.prompt.SimpleSearchableListView
import hextant.fx.initHextantScene
import hextant.serial.EditorRoot
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.Node
import javafx.scene.control.Button
import javafx.scene.control.Label
import javafx.scene.control.ScrollPane
import javafx.scene.input.DragEvent
import javafx.scene.input.Dragboard
import javafx.scene.layout.BorderPane
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import org.controlsfx.control.ToggleSwitch
import org.kordamp.ikonli.codicons.Codicons
import reaktive.value.ReactiveVariable
import reaktive.value.fx.asProperty
import reaktive.value.reactiveValue
import reaktive.value.reactiveVariable
import xenakis.impl.asTime
import xenakis.model.obj.*
import xenakis.model.registry.*
import xenakis.model.score.*
import xenakis.sc.BufferControlSpec
import xenakis.sc.ControlSpec
import xenakis.sc.NumericalControlSpec
import xenakis.sc.editor.BusSelector
import xenakis.sc.editor.GroupSelector
import xenakis.sc.editor.SampleSelector
import xenakis.sc.editor.ScExprExpander
import xenakis.sc.view.ObjectSelectorControl
import xenakis.ui.impl.colorPicker

class ControlAssignmentEditor(
    private val obj: ParameterizedObject,
    val parameter: String,
) : BorderPane() {
    private val nameLabel = Label(parameter)
    private var selectedOption: ControlType<*>? = null
    private val optionButton = Button() styleClass "sleek-button"
    private val detailEditors = mutableMapOf<ControlType<*>, Node>()
    private val spec
        get() = obj.getSpec(parameter) ?: error("Parameter $parameter not found in $obj")
    private val actionBar: ActionBar = ActionBar(actions.withContext(this), buttonStyle = "medium-icon-button")
    private var settingControl = false
    private var detailEditor: Node? = null
        set(value) {
            field = value!!
            value.styleClass?.add("control-detail-editor")
            center = HBox(detailEditor).also { it.padding = Insets(0.0, 5.0, 0.0, 5.0) }.centerChildren()
            HBox.setHgrow(detailEditor, Priority.ALWAYS)
            val box = HBox(nameLabel).centerChildren()
            if (spec is NumericalControlSpec) box.children.add(optionButton)
            left = box
        }

    init {
        styleClass.add("detail-item")
        optionButton.isFocusTraversable = false
        optionButton.setOnMouseClicked { showOptionPopup() }
        optionButton.minWidth = 85.0
        nameLabel.minWidth = DetailPane.LABEL_WIDTH - 5.0
        right = actionBar
        setupDropArea(this::canDrop, ::onDrop)
    }

    private fun onDrop(ev: DragEvent) {
        val db = ev.dragboard
        val samples = obj.context[SampleRegistry]
        val sample =
            if (db.hasFile("wav")) samples.getOrAdd(db.files[0])
            else if (db.hasContent(SampleObject.DATA_FORMAT)) samples.get(db.getContent(SampleObject.DATA_FORMAT) as String)
            else return
        val ctrl = obj.controls[parameter] as BufferControl
        ctrl.sample.set(sample.reference())
    }

    private fun canDrop(db: Dragboard): Boolean {
        if (obj.controls[parameter] !is BufferControl) return false
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
        val oldControl = obj.controls[parameter]
        val control = t.createDefaultControl(obj, spec, oldControl)
        obj.controls.reassignControl(parameter, control)
    }

    fun setControl(control: ParameterControl) {
        val type = ControlType.getType(control)
        settingControl = true
        optionButton.text = type.toString()
        detailEditor = type.createDetailInput(obj, parameter, spec, control)
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
            spec: ControlSpec,
            control: C
        ): Node

        abstract fun createDefaultControl(obj: ParameterizedObject, spec: ControlSpec, oldControl: ParameterControl): C

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
                spec: ControlSpec,
                control: ConstantControl
            ): Node {
                val converter = (spec as NumericalControlSpec).converter()
                val sliderBar = SliderBar(
                    control.value, reactiveValue(parameter), converter,
                    style = SliderBar.Style.AlwaysValue
                )
                sliderBar.prefWidth = 80.0
                return sliderBar
            }

            override fun createDefaultControl(
                obj: ParameterizedObject, spec: ControlSpec, oldControl: ParameterControl
            ): ConstantControl {
                spec as NumericalControlSpec
                return ConstantControl(reactiveVariable(oldControl.getNumericalValue() ?: spec.defaultValue.get()))
            }
        }

        object Envelope : ControlType<EnvelopeControl>() {
            override fun createDetailInput(
                obj: ParameterizedObject,
                parameter: String,
                spec: ControlSpec,
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
                spec: ControlSpec,
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
                spec: ControlSpec,
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
                spec: ControlSpec,
                oldControl: ParameterControl
            ): CustomControl {
                val editor = ScExprExpander()
                val root = EditorRoot.create(editor, obj.context)
                if (oldControl.getNumericalValue() != null) {
                    editor.setText(oldControl.getNumericalValue().toString())
                }
                return CustomControl(root)
            }
        }

        object Bus : ControlType<BusControl>() {
            override fun createDetailInput(
                obj: ParameterizedObject,
                parameter: String,
                spec: ControlSpec,
                control: BusControl
            ): Node = busSelector(control.bus)

            override fun createDefaultControl(
                obj: ParameterizedObject,
                spec: ControlSpec,
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
                spec: ControlSpec,
                control: BusValueControl
            ): Node = busSelector(control.bus)

            override fun createDefaultControl(
                obj: ParameterizedObject,
                spec: ControlSpec,
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
                spec: ControlSpec,
                control: SingleBusValueControl
            ): Node = busSelector(control.bus)

            override fun createDefaultControl(
                obj: ParameterizedObject, spec: ControlSpec, oldControl: ParameterControl
            ): SingleBusValueControl {
                val initial = oldControl.getBus() ?: obj.context[BusRegistry].getDefault().reference()
                return SingleBusValueControl(reactiveVariable(initial))
            }

        }

        object Buffer : ControlType<BufferControl>() {
            override fun createDetailInput(
                obj: ParameterizedObject,
                parameter: String,
                spec: ControlSpec,
                control: BufferControl
            ): Node {
                val editor = SampleSelector()
                editor.syncWith(control.sample)
                val selectorControl = ObjectSelectorControl(editor, createBundle())
                val displaySwitch = ToggleSwitch("Display: ")
                spec as BufferControlSpec
                displaySwitch.selectedProperty().bindBidirectional(control.display.asProperty())
                return HBox(selectorControl, infiniteSpace(), displaySwitch).centerChildren()
            }

            override fun createDefaultControl(
                obj: ParameterizedObject,
                spec: ControlSpec,
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
                spec: ControlSpec,
                control: GroupControl
            ): Node {
                val selector = GroupSelector()
                selector.syncWith(control.group)
                return ObjectSelectorControl(selector, createBundle())
            }

            override fun createDefaultControl(
                obj: ParameterizedObject,
                spec: ControlSpec,
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

            private fun busSelector(control: ReactiveVariable<BusReference>): ObjectSelectorControl<BusObject> {
                val editor = BusSelector()
                editor.syncWith(control)
                return ObjectSelectorControl(editor, createBundle())
            }
        }
    }

    companion object {
        private val actions = collectActions<ControlAssignmentEditor> {
            addAction("Edit spec") {
                shortcut("Ctrl+P")
                applicableIf { editor -> reactiveValue(editor.spec is NumericalControlSpec) }
                //editor.obj.def.getParameter(editor.parameter)!!.spec.map { s -> s is NumericalControlSpec }
                icon(Codicons.SYMBOL_PROPERTY)
                executes { editor: ControlAssignmentEditor ->
                    ControlSpecPrompt(editor.obj, editor.parameter, editor.spec)
                        .showDialog(anchorNode = editor)
                }
            }
        }
    }
}