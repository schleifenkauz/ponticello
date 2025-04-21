package xenakis.ui.controls

import bundles.createBundle
import fxutils.*
import fxutils.actions.ActionBar
import fxutils.actions.ContextualizedAction
import fxutils.actions.collectActions
import fxutils.controls.SliderBar
import fxutils.prompt.InfoPrompt
import fxutils.prompt.SimpleSearchableListView
import hextant.context.Context
import hextant.serial.EditorRoot
import javafx.geometry.Pos
import javafx.scene.Node
import javafx.scene.control.Button
import javafx.scene.control.Label
import javafx.scene.input.DragEvent
import javafx.scene.input.Dragboard
import javafx.scene.layout.HBox
import javafx.scene.paint.Color
import org.controlsfx.control.ToggleSwitch
import org.kordamp.ikonli.evaicons.Evaicons
import org.kordamp.ikonli.materialdesign2.MaterialDesignS
import reaktive.value.*
import reaktive.value.fx.asProperty
import xenakis.impl.asTime
import xenakis.impl.one
import xenakis.impl.zero
import xenakis.model.obj.*
import xenakis.model.player.ActiveScoreObject
import xenakis.model.player.PlaybackManager
import xenakis.model.registry.*
import xenakis.model.score.ParameterControlList.NamedParameterControl
import xenakis.model.score.ScoreObject
import xenakis.model.score.SynthObject
import xenakis.model.score.controls.*
import xenakis.sc.*
import xenakis.sc.editor.*
import xenakis.sc.view.ObjectSelectorControl
import xenakis.ui.actions.ServerActions
import xenakis.ui.impl.colorPicker
import xenakis.ui.impl.makeSubWindow
import xenakis.ui.impl.sceneFill
import xenakis.ui.misc.CodePane
import xenakis.ui.score.ScoreObjectView

class ControlAssignmentEditor(val control: NamedParameterControl, val view: ScoreObjectView?) : HBox() {
    private var selectedOption: ControlType<*>? = null
    private val optionButton = Button() styleClass "sleek-button"
    private val detailEditors = mutableMapOf<ControlType<*>, Node>()
    private val spec get() = control.spec.now
    private var settingControl = false
    private var detailEditor: Node? = null

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
        detailEditor?.requestFocus()
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
        detailEditor =
            if (control.spec.now == null) missingSpecOptionsBar(control)
            else type.createDetailInput(control, newControl, view)
        detailEditors[type] = detailEditor!!
        children.clear()
        if (spec is NumericalControlSpec) children.add(optionButton)
        val actions = type.actions(control, newControl, view)
        children.add(detailEditor)
        if (actions.isNotEmpty()) children.add(ActionBar(actions, "medium-icon-button"))
        settingControl = false
    }

    fun focusInputControl(): Boolean {
        detailEditor?.requestFocus()
        return detailEditor != null
    }

    sealed class ControlType<C : ParameterControl> {
        abstract fun createDetailInput(
            namedControl: NamedParameterControl,
            control: C,
            view: ScoreObjectView?,
        ): Node

        open fun actions(
            namedControl: NamedParameterControl, control: C,
            view: ScoreObjectView?,
        ): List<ContextualizedAction> = emptyList()

        abstract fun createDefaultControl(obj: ParameterizedObject, spec: ControlSpec?, oldControl: ParameterControl): C

        data object Value : ControlType<ValueControl>() {
            override fun createDetailInput(
                namedControl: NamedParameterControl,
                control: ValueControl,
                view: ScoreObjectView?,
            ): Node {
                val spec = namedControl.spec.now
                if (spec !is NumericalControlSpec) return missingSpecOptionsBar(namedControl)
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

        data object Envelope : ControlType<EnvelopeControl>() {
            override fun createDetailInput(
                namedControl: NamedParameterControl,
                control: EnvelopeControl,
                view: ScoreObjectView?,
            ): Node {
                if (namedControl.spec.now !is NumericalControlSpec) return missingSpecOptionsBar(namedControl)
                val colorPicker = colorPicker(control.displayColor)
                colorPicker.setFixedWidth(30.0)
                val box = HBox(5.0, colorPicker)
                if (namedControl.parentObject is SynthObject) {
                    val toggle = ToggleSwitch("Display: ")
                    toggle.selectedProperty().bindBidirectional(control.display.asProperty())
                    box.children.add(1, toggle)
                }
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

            override fun actions(
                namedControl: NamedParameterControl,
                control: EnvelopeControl,
                view: ScoreObjectView?,
            ): List<ContextualizedAction> = actions.withContext(control)

            private val actions = collectActions<EnvelopeControl> {
                addAction("Update") {
                    icon(MaterialDesignS.SYNC)
                    shortcut("Ctrl+U")
                    executes { ctrl -> ctrl.update.fire() }
                }
            }
        }

        data object UGen : ControlType<UGenControl>() {
            override fun createDetailInput(
                namedControl: NamedParameterControl,
                control: UGenControl,
                view: ScoreObjectView?,
            ): Node {
                val actions = actions.withContext(Pair(namedControl, view))
                val pane = CodePane(
                    control.expr,
                    extraActions = actions,
                    actionBarAlignment = Pos.BOTTOM_RIGHT,
                    ownWindow = true
                )
                val window = makeSubWindow(pane, "LFO for ${namedControl.name.now}", control.context)
                window.sceneFill(Color.BLACK)
                window.resize(300.0, 150.0)
                return button("Code") { window.showOrBringToFront() }
            }

            override fun createDefaultControl(
                obj: ParameterizedObject,
                spec: ControlSpec?,
                oldControl: ParameterControl,
            ): UGenControl {
                val editor = ScExprExpander()
                val root = EditorRoot(editor)
                if (oldControl.getNumericalValue() != null) {
                    editor.setInitialText(oldControl.getNumericalValue().toString())
                } else editor.setInitialText("")
                return UGenControl(root)
            }

            override fun actions(
                namedControl: NamedParameterControl,
                control: UGenControl,
                view: ScoreObjectView?,
            ): List<ContextualizedAction> = actions.withContext(Pair(namedControl, view))

            private val actions = collectActions<Pair<NamedParameterControl, ScoreObjectView?>> {
                addAction("Update") {
                    icon(MaterialDesignS.SYNC)
                    shortcut("Ctrl+U")
                    executes { (ctrl) ->
                        val ugen = ctrl.now as UGenControl
                        ugen.update.fire()
                    }
                }
                addAction("Scope") {
                    icon(Evaicons.ACTIVITY)
                    shortcut("Ctrl+E")
                    applicableIf { (_, view) -> reactiveValue(view != null) }
                    executes { (ctrl, view), ev ->
                        val ugen = ctrl.now as UGenControl
                        val activeObjects = ctrl.parentObject.activeObjects()
                        val activeObject = if (view != null) {
                            val playbackManager = ctrl.context[PlaybackManager]
                            val positionRelativeToPlayedScore =
                                view.absolutePosition - playbackManager.positionOfPlayedScore
                            activeObjects.find { obj ->
                                obj is ActiveScoreObject && obj.absolutePosition == positionRelativeToPlayedScore
                            }
                        } else activeObjects.singleOrNull()
                        val parameter = ctrl.name.now
                        if (activeObject != null) {
                            ugen.scope(activeObject, parameter)
                        } else {
                            InfoPrompt("Object is not played currently").showDialog(ev)
                        }
                    }
                }
            }

        }

        data object Bus : ControlType<BusControl>() {
            override fun createDetailInput(
                namedControl: NamedParameterControl,
                control: BusControl,
                view: ScoreObjectView?,
            ): Node = busSelector(control.bus, namedControl.spec.now, namedControl.context)

            override fun createDefaultControl(
                obj: ParameterizedObject,
                spec: ControlSpec?,
                oldControl: ParameterControl,
            ): BusControl {
                val initial = oldControl.getBus() ?: obj.context[BusRegistry].getDefault().reference()
                return BusControl(reactiveVariable(initial))
            }

            override fun actions(
                namedControl: NamedParameterControl,
                control: BusControl,
                view: ScoreObjectView?,
            ): List<ContextualizedAction> = listOf(ServerActions.scopeBus.withContext(control.bus.now))
        }

        data object BusValue : ControlType<BusValueControl>() {
            override fun createDetailInput(
                namedControl: NamedParameterControl,
                control: BusValueControl,
                view: ScoreObjectView?,
            ): Node = busSelector(control.bus, namedControl.spec.now, namedControl.context)

            override fun createDefaultControl(
                obj: ParameterizedObject,
                spec: ControlSpec?,
                oldControl: ParameterControl,
            ): BusValueControl {
                val initial = oldControl.getBus() ?: obj.context[BusRegistry].getDefault().reference()
                return BusValueControl(reactiveVariable(initial))
            }

            override fun toString(): String = "Bus"

            override fun actions(
                namedControl: NamedParameterControl,
                control: BusValueControl,
                view: ScoreObjectView?,
            ): List<ContextualizedAction> = listOf(ServerActions.scopeBus.withContext(control.bus.now))
        }

        data object SingleBusValue : ControlType<SingleBusValueControl>() {
            override fun createDetailInput(
                namedControl: NamedParameterControl,
                control: SingleBusValueControl,
                view: ScoreObjectView?,
            ): Node = busSelector(control.bus, namedControl.spec.now, namedControl.context)

            override fun createDefaultControl(
                obj: ParameterizedObject, spec: ControlSpec?, oldControl: ParameterControl,
            ): SingleBusValueControl {
                val initial = oldControl.getBus() ?: obj.context[BusRegistry].getDefault().reference()
                return SingleBusValueControl(reactiveVariable(initial))
            }

            override fun actions(
                namedControl: NamedParameterControl,
                control: SingleBusValueControl,
                view: ScoreObjectView?,
            ): List<ContextualizedAction> = listOf(ServerActions.scopeBus.withContext(control.bus.now))

            override fun toString(): String = "Bus Value"
        }

        data object Buffer : ControlType<BufferControl>() {
            override fun createDetailInput(
                namedControl: NamedParameterControl,
                control: BufferControl,
                view: ScoreObjectView?,
            ): Node {
                val editor = BufferSelector()
                val spec = namedControl.spec.now as BufferControlSpec
                editor.setFilter(spec.channels)
                editor.syncWith(control.sample)
                editor.initialize(namedControl.context)
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

        data object Group : ControlType<GroupControl>() {
            override fun createDetailInput(
                namedControl: NamedParameterControl,
                control: GroupControl,
                view: ScoreObjectView?,
            ): Node {
                val selector = GroupSelector()
                selector.syncWith(control.group)
                selector.initialize(namedControl.context)
                return ObjectSelectorControl(selector, createBundle())
            }

            override fun createDefaultControl(
                obj: ParameterizedObject,
                spec: ControlSpec?,
                oldControl: ParameterControl,
            ): GroupControl = GroupControl(reactiveVariable(obj.context[GroupRegistry].getDefault().reference()))
        }

        data object GlobalPattern : ControlType<GlobalPatternControl>() {
            override fun createDetailInput(
                namedControl: NamedParameterControl,
                control: GlobalPatternControl,
                view: ScoreObjectView?,
            ): Node {
                val selector = GlobalPatternSelector()
                selector.syncWith(control.pattern)
                selector.initialize(namedControl.context)
                return ObjectSelectorControl(selector, createBundle())
            }

            override fun createDefaultControl(
                obj: ParameterizedObject,
                spec: ControlSpec?,
                oldControl: ParameterControl,
            ): GlobalPatternControl = GlobalPatternControl(reactiveVariable(ObjectReference.none()))

            override fun toString(): String = "Pattern"
        }

        data object AttackRelease : ControlType<AttackReleaseControl>() {
            override fun createDetailInput(
                namedControl: NamedParameterControl,
                control: AttackReleaseControl,
                view: ScoreObjectView?,
            ): Node {
                val box = HBox(10.0)
                val spec = namedControl.spec.now as? NumericalControlSpec
                    ?: return missingSpecOptionsBar(namedControl)
                box.userData = namedControl.parentObject.duration()!!.forEach { duration ->
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
                    control.release.now = control.release.now.coerceAtMost(view!!.obj.duration().now - attack)
                } and control.release.observe { _, _, release ->
                    control.attack.now = control.attack.now.coerceAtMost(view!!.obj.duration().now - release)
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

            override fun toString(): String = "ASR"
        }

        companion object {
            val all: List<ControlType<*>> = listOf(
                Value, Envelope, AttackRelease,
                BusValue, SingleBusValue,
                GlobalPattern, UGen
            )

            @Suppress("UNCHECKED_CAST")
            fun <O : ParameterControl> getType(option: O) = when (option) {
                is ValueControl -> Value
                is UGenControl -> UGen
                is EnvelopeControl -> Envelope
                is BusControl -> Bus
                is BusValueControl -> BusValue
                is SingleBusValueControl -> SingleBusValue
                is BufferControl -> Buffer
                is GroupControl -> Group
                is GlobalPatternControl -> GlobalPattern
                is AttackReleaseControl -> AttackRelease
                else -> throw AssertionError()
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
        }
    }

    companion object {
        private fun missingSpecOptionsBar(control: NamedParameterControl): HBox = HBox(
            5.0,
            Label("Invalid or unresolved spec"),
            button("Use spec from definition") { ev ->
                val success = control.useSpecFromDefinition()
                if (!success) {
                    InfoPrompt("No spec found in '${control.parentObject.def.name.now}'").showDialog(ev)
                }
            },
            button("Provide custom spec") { ev ->
                val spec = NumericalControlSpecPrompt(
                    control.name.now, control.parentObject, NumericalControlSpec.DEFAULT,
                    "Provide custom specification"
                ).showDialog(ev) ?: return@button
                control.setCustomSpec(spec)
            }
        ).centerChildren()
    }
}