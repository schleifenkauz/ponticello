package xenakis.ui.controls

import bundles.createBundle
import fxutils.*
import fxutils.actions.ActionBar
import fxutils.actions.collectActions
import fxutils.controls.SliderBar
import fxutils.prompt.InfoPrompt
import fxutils.prompt.SimpleSearchableListView
import hextant.context.Context
import hextant.fx.initHextantScene
import hextant.serial.EditorRoot
import javafx.geometry.Pos
import javafx.scene.Node
import javafx.scene.control.Button
import javafx.scene.control.Label
import javafx.scene.control.ScrollPane
import javafx.scene.input.DragEvent
import javafx.scene.input.Dragboard
import javafx.scene.layout.BorderPane
import javafx.scene.layout.HBox
import javafx.scene.layout.Region
import javafx.scene.paint.Color
import org.controlsfx.control.ToggleSwitch
import org.kordamp.ikonli.materialdesign2.MaterialDesignD
import reaktive.value.*
import reaktive.value.binding.map
import reaktive.value.fx.asProperty
import xenakis.impl.asTime
import xenakis.impl.one
import xenakis.impl.zero
import xenakis.model.obj.*
import xenakis.model.registry.*
import xenakis.model.score.ParameterControlList.NamedParameterControl
import xenakis.model.score.ScoreObject
import xenakis.model.score.controls.*
import xenakis.sc.*
import xenakis.sc.editor.BufferSelector
import xenakis.sc.editor.BusSelector
import xenakis.sc.editor.GroupSelector
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
            //setHgrow(detailEditor, Priority.ALWAYS)
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
        val samples = control.context[BufferRegistry]
        val sample =
            when {
                db.hasFile("wav") -> samples.getOrAdd(db.files[0])
                db.hasContent(BufferObject.DATA_FORMAT) -> samples.get(db.getContent(BufferObject.DATA_FORMAT) as String)
                else -> return
            }
        val ctrl = control.now as BufferControl
        ctrl.sample.set(sample.reference())
    }

    private fun canDrop(db: Dragboard): Boolean {
        if (control.now !is BufferControl) return false
        return db.hasFile("wav") || db.hasContent(BufferObject.DATA_FORMAT)
    }

    private fun showOptionPopup() {
        val listView = SimpleSearchableListView(ControlType.all, "Select control type")
        val option = listView.showPopup(
            anchorNode = optionButton,
            initialOption = selectedOption
        ) ?: return
        updateControlType(option)
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
        detailEditor = type.createDetailInput(control.parentObject, control, newControl)
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
            namedControl: NamedParameterControl,
            control: C,
        ): Node

        abstract fun createDefaultControl(obj: ParameterizedObject, spec: ControlSpec?, oldControl: ParameterControl): C

        override fun toString(): String = when (this) {
            Buffer -> "Buffer"
            Bus -> "Bus"
            BusValue -> "Bus"
            Value -> "Value"
            Envelope -> "Envelope"
            Group -> "Group"
            LFO -> "Expr"
            SingleBusValue -> "Bus Value"
            AttackRelease -> "ASR"
        }

        object Value : ControlType<ValueControl>() {
            override fun createDetailInput(
                obj: ParameterizedObject,
                namedControl: NamedParameterControl,
                control: ValueControl,
            ): Node {
                val spec = namedControl.spec.now
                if (spec !is NumericalControlSpec) return missingSpecOptionsBar(obj, namedControl)
                val converter = spec.converter()
                val sliderBar = SliderBar(
                    control.value, namedControl.name, converter,
                    style = SliderBar.Style.AlwaysValue
                )
                sliderBar.prefWidth = 150.0
                return sliderBar
            }

            override fun createDefaultControl(
                obj: ParameterizedObject, spec: ControlSpec?, oldControl: ParameterControl,
            ): ValueControl {
                spec as NumericalControlSpec
                return ValueControl(reactiveVariable(oldControl.getNumericalValue() ?: spec.defaultValue.get()))
            }
        }

        object Envelope : ControlType<EnvelopeControl>() {
            override fun createDetailInput(
                obj: ParameterizedObject,
                namedControl: NamedParameterControl,
                control: EnvelopeControl,
            ): Node {
                if (namedControl.spec.now !is NumericalControlSpec) return missingSpecOptionsBar(obj, namedControl)
                val colorPicker = colorPicker(control.displayColor)
                colorPicker.setFixedWidth(30.0)
                val toggle = ToggleSwitch("Display: ")
                toggle.selectedProperty().bindBidirectional(control.display.asProperty())
                val box = HBox(5.0, colorPicker, toggle)
                box.alignment = Pos.CENTER_LEFT
                return box
            }

            override fun createDefaultControl(
                obj: ParameterizedObject,
                spec: ControlSpec?,
                oldControl: ParameterControl,
            ): EnvelopeControl {
                spec as NumericalControlSpec
                val value = oldControl.getNumericalValue() ?: spec.defaultValue.get()
                val duration = (obj as? ScoreObject)?.duration ?: 1.0.asTime
                val env = xenakis.model.score.Envelope.constant(value, duration)
                val displayColor = reactiveVariable(spec.associatedColor)
                val display = reactiveVariable(true)
                return EnvelopeControl(env, displayColor, display)
            }
        }

        object LFO : ControlType<CustomControl>() {
            override fun createDetailInput(
                obj: ParameterizedObject,
                namedControl: NamedParameterControl,
                control: CustomControl,
            ): Node {
                val pane = ScrollPane(control.expr.control)
                pane.background = background(Color.BLACK)
                val window = SubWindow(Region(), "LFO for ${namedControl.name.now}")
                window.scene.initHextantScene(obj.context)
                window.resize(500.0, 200.0)
                val showWindowButton = button("Code") { window.showOrBringToFront() }
                val actionBar = ActionBar(actions.withContext(control), "medium-icon-button")
                val box = HBox(actionBar, infiniteSpace()).centerChildren()
                box.userData = control.subWindow.forEach { subWindow ->
                    if (subWindow) {
                        box.children.remove(pane)
                        box.children.add(0, showWindowButton)
                        val layout = BorderPane(pane)
                        window.scene.root = layout
                    } else {
                        window.scene.root = Region()
                        box.children.remove(showWindowButton)
                        box.children.add(0, pane)
                    }
                }
                return box
            }

            override fun createDefaultControl(
                obj: ParameterizedObject,
                spec: ControlSpec?,
                oldControl: ParameterControl,
            ): CustomControl {
                val editor = ScExprExpander()
                val root = EditorRoot(editor)
                if (oldControl.getNumericalValue() != null) {
                    editor.setInitialText(oldControl.getNumericalValue().toString())
                } else editor.setInitialText("")
                return CustomControl(root, reactiveVariable(false))
            }

            private val actions = collectActions<CustomControl> {
                addAction("Toggle sub window") {
                    description { ctrl ->
                        ctrl.subWindow.map { subWindow ->
                            if (subWindow) "Show inline" else "Show in sub window"
                        }
                    }
                    icon(MaterialDesignD.DOCK_WINDOW)
                    toggles(CustomControl::subWindow)
                }
            }
        }

        object Bus : ControlType<BusControl>() {
            override fun createDetailInput(
                obj: ParameterizedObject,
                namedControl: NamedParameterControl,
                control: BusControl,
            ): Node = busSelector(control.bus, namedControl.spec.now, obj.context)

            override fun createDefaultControl(
                obj: ParameterizedObject,
                spec: ControlSpec?,
                oldControl: ParameterControl,
            ): BusControl {
                val initial = oldControl.getBus() ?: obj.context[BusRegistry].getDefault().reference()
                return BusControl(reactiveVariable(initial))
            }
        }

        object BusValue : ControlType<BusValueControl>() {
            override fun createDetailInput(
                obj: ParameterizedObject,
                namedControl: NamedParameterControl,
                control: BusValueControl,
            ): Node = busSelector(control.bus, namedControl.spec.now, obj.context)

            override fun createDefaultControl(
                obj: ParameterizedObject,
                spec: ControlSpec?,
                oldControl: ParameterControl,
            ): BusValueControl {
                val initial = oldControl.getBus() ?: obj.context[BusRegistry].getDefault().reference()
                return BusValueControl(reactiveVariable(initial))
            }
        }

        object SingleBusValue :
            ControlType<SingleBusValueControl>() {
            override fun createDetailInput(
                obj: ParameterizedObject,
                namedControl: NamedParameterControl,
                control: SingleBusValueControl,
            ): Node = busSelector(control.bus, namedControl.spec.now, obj.context)

            override fun createDefaultControl(
                obj: ParameterizedObject, spec: ControlSpec?, oldControl: ParameterControl,
            ): SingleBusValueControl {
                val initial = oldControl.getBus() ?: obj.context[BusRegistry].getDefault().reference()
                return SingleBusValueControl(reactiveVariable(initial))
            }

        }

        object Buffer : ControlType<BufferControl>() {
            override fun createDetailInput(
                obj: ParameterizedObject,
                namedControl: NamedParameterControl,
                control: BufferControl,
            ): Node {
                val editor = BufferSelector()
                val spec = namedControl.spec.now as BufferControlSpec
                editor.setFilter(spec.channels)
                editor.syncWith(control.sample)
                editor.initialize(obj.context)
                val selectorControl = ObjectSelectorControl(editor, createBundle())
                val displaySwitch = ToggleSwitch("Display: ")
                displaySwitch.selectedProperty().bindBidirectional(control.display.asProperty())
                return HBox(5.0, selectorControl, infiniteSpace(), displaySwitch).centerChildren()
            }

            override fun createDefaultControl(
                obj: ParameterizedObject,
                spec: ControlSpec?,
                oldControl: ParameterControl,
            ): BufferControl {
                spec as BufferControlSpec
                val display = reactiveVariable(spec.isPlayBufSource)
                val sample: ReactiveVariable<BufferReference> = reactiveVariable(ObjectReference.none())
                return BufferControl(sample, display)
            }
        }

        object Group : ControlType<GroupControl>() {
            override fun createDetailInput(
                obj: ParameterizedObject,
                namedControl: NamedParameterControl,
                control: GroupControl,
            ): Node {
                val selector = GroupSelector()
                selector.syncWith(control.group)
                selector.initialize(obj.context)
                return ObjectSelectorControl(selector, createBundle())
            }

            override fun createDefaultControl(
                obj: ParameterizedObject,
                spec: ControlSpec?,
                oldControl: ParameterControl,
            ): GroupControl =
                GroupControl(reactiveVariable(obj.context[GroupRegistry].getDefault().reference()))
        }

        object AttackRelease : ControlType<AttackReleaseControl>() {
            override fun createDetailInput(
                obj: ParameterizedObject,
                namedControl: NamedParameterControl,
                control: AttackReleaseControl,
            ): Node {
                val box = HBox(10.0)
                val spec = namedControl.spec.now as? NumericalControlSpec
                    ?: return missingSpecOptionsBar(obj, namedControl)
                box.userData = obj.duration()!!.forEach { duration ->
                    val timeSpec = NumericalControlSpec(
                        default = zero, min = zero, max = duration,
                        step = 0.01.asTime, warp = Warp.Linear, associatedColor = Color.GRAY
                    ).converter()
                    control.attack.now = control.attack.now.coerceAtMost(duration)
                    control.release.now = control.release.now.coerceAtMost(duration - control.attack.now)
                    val levelSpec = spec.converter()
                    val level = SliderBar(control.level, reactiveValue("Level"), levelSpec)
                    val attack = SliderBar(control.attack, reactiveValue("Attack"), timeSpec)
                    val release = SliderBar(control.release, reactiveValue("Release"), timeSpec)
                    level.prefWidth = 100.0
                    attack.prefWidth = 100.0
                    release.prefWidth = 100.0
                    box.children.setAll(level, attack, release)
                } and control.attack.observe { _, _, attack ->
                    control.release.now = control.release.now.coerceAtMost(obj.duration()!!.now - attack)
                } and control.release.observe { _, _, release ->
                    control.attack.now = control.attack.now.coerceAtMost(obj.duration()!!.now - release)
                }
                return box.centerChildren()
            }

            override fun createDefaultControl(
                obj: ParameterizedObject,
                spec: ControlSpec?,
                oldControl: ParameterControl,
            ): AttackReleaseControl {
                val level = oldControl.getNumericalValue() ?: one
                return AttackReleaseControl(reactiveVariable(zero), reactiveVariable(zero), reactiveVariable(level))
            }
        }

        companion object {
            val all: List<ControlType<*>> = listOf(Value, LFO, Envelope, BusValue, SingleBusValue, AttackRelease)

            @Suppress("UNCHECKED_CAST")
            fun <O : ParameterControl> getType(option: O) = when (option) {
                is ValueControl -> Value
                is CustomControl -> LFO
                is EnvelopeControl -> Envelope
                is BusControl -> Bus
                is BusValueControl -> BusValue
                is SingleBusValueControl -> SingleBusValue
                is BufferControl -> Buffer
                is GroupControl -> Group
                is AttackReleaseControl -> AttackRelease
            } as ControlType<O>

            private fun busSelector(
                control: ReactiveVariable<BusReference>,
                spec: ControlSpec?, context: Context,
            ): ObjectSelectorControl<BusObject> {
                val editor = BusSelector()
                if (spec is BusControlSpec) editor.setFilter(spec.rate, spec.channels)
                else editor.setFilter(rate = Rate.Control, channels = 1)
                editor.syncWith(control)
                editor.initialize(context)
                return ObjectSelectorControl(editor, createBundle())
            }

            private fun missingSpecOptionsBar(obj: ParameterizedObject, control: NamedParameterControl): HBox = HBox(
                5.0,
                Label("No spec found"),
                button("Use spec from definition") { ev ->
                    val success = control.useSpecFromDefinition()
                    if (!success) {
                        InfoPrompt("No spec found in '${obj.def.name.now}'").showDialog(ev)
                    }
                },
                button("Provide custom spec") { ev ->
                    val spec = NumericalControlSpecPrompt(
                        control.name.now, obj, NumericalControlSpec.DEFAULT,
                        "Provide custom specification"
                    ).showDialog(ev) ?: return@button
                    control.setCustomSpec(spec)
                }
            ).centerChildren()
        }
    }
}