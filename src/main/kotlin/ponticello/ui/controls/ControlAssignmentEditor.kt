package ponticello.ui.controls

import bundles.createBundle
import fxutils.*
import fxutils.actions.ActionBar
import fxutils.actions.ContextualizedAction
import fxutils.actions.action
import fxutils.actions.collectActions
import fxutils.controls.SliderBar
import fxutils.prompt.InfoPrompt
import fxutils.prompt.SimpleSearchableListView
import fxutils.undo.UndoManager
import hextant.context.Context
import hextant.context.compoundEdit
import hextant.serial.EditorRoot
import javafx.geometry.Pos
import javafx.scene.Node
import javafx.scene.control.Button
import javafx.scene.control.CheckBox
import javafx.scene.control.Label
import javafx.scene.input.DragEvent
import javafx.scene.input.Dragboard
import javafx.scene.layout.HBox
import javafx.scene.paint.Color
import org.kordamp.ikonli.evaicons.Evaicons
import org.kordamp.ikonli.material2.Material2AL
import org.kordamp.ikonli.materialdesign2.MaterialDesignC
import org.kordamp.ikonli.materialdesign2.MaterialDesignE
import org.kordamp.ikonli.materialdesign2.MaterialDesignS
import ponticello.impl.*
import ponticello.model.obj.*
import ponticello.model.player.ActiveObject
import ponticello.model.player.ActiveScoreObject
import ponticello.model.project.mainScore
import ponticello.model.registry.*
import ponticello.model.score.ParameterControlList.NamedParameterControl
import ponticello.model.score.ScoreObject
import ponticello.model.score.SynthObject
import ponticello.model.score.controls.*
import ponticello.sc.*
import ponticello.sc.editor.BufferSelector
import ponticello.sc.editor.BusSelector
import ponticello.sc.editor.GlobalPatternSelector
import ponticello.sc.editor.ScExprExpander
import ponticello.sc.view.ObjectSelectorControl
import ponticello.ui.actions.ServerActions
import ponticello.ui.impl.colorPicker
import ponticello.ui.impl.makeSubWindow
import ponticello.ui.impl.sceneFill
import ponticello.ui.launcher.PonticelloLauncher.Companion.currentProject
import ponticello.ui.launcher.PonticelloMainActivity
import ponticello.ui.misc.CodePane
import ponticello.ui.registry.SimpleSearchableRegistryView
import ponticello.ui.score.ScoreObjectView
import reaktive.value.*
import reaktive.value.binding.flatMap
import reaktive.value.binding.map
import reaktive.value.binding.orElse
import reaktive.value.fx.asObservableValue
import reaktive.value.fx.asProperty

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
        val options = ControlType.all.filter { option -> option.applicableOn(control.parentObject) }
        val listView = SimpleSearchableListView(options, "Select control type")
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
        open fun applicableOn(obj: ParameterizedObject): Boolean = true

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
                    style = SliderBar.Style.AlwaysValue,
                    undoManager = namedControl.context[UndoManager]
                )
                sliderBar.prefWidth = 150.0
                val allocateBusOption = CheckBox()
                    .sync(control.allocateBus, description = "Allocate bus", namedControl.context[UndoManager])
                return HBox(5.0, sliderBar, Label("Allocate bus"), allocateBusOption).centerChildren()
            }

            override fun createDefaultControl(
                obj: ParameterizedObject, spec: ControlSpec?, oldControl: ParameterControl,
            ): ValueControl {
                spec as NumericalControlSpec
                return ValueControl(reactiveVariable(oldControl.getNumericalValue() ?: spec.defaultValue.get()))
            }
        }

        data object Envelope : ControlType<EnvelopeControl>() {
            override fun applicableOn(obj: ParameterizedObject): Boolean = obj is ScoreObject

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
                    val toggle = CheckBox()
                        .sync(control.display, description = "Display envelope", namedControl.context[UndoManager])
                    box.children.addAll(1, listOf(Label("Display envelope"), toggle))
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
                val env = ponticello.model.score.Envelope.constant(value, duration)
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
                window.minWidth = 100.0
                window.minHeight = 100.0
                val showWindowButton = button("Code") { window.showOrBringToFront() }
                if (namedControl.parentObject is ScoreObject) {
                    val displayToggle = CheckBox()
                        .sync(control.display, "Display UGen", namedControl.context[UndoManager])
                    displayToggle.disableProperty().bind(
                        control.expr.editor.result.map { expr ->
                            expr.getLfo() == null
                        }.asObservableValue()
                    )
                    return HBox(5.0, showWindowButton, Label("Display"), displayToggle).centerChildren()
                } else return showWindowButton
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
                    executes { (ctrl, view), ev ->
                        val ugen = ctrl.now as UGenControl
                        val activeObject = getActiveObject(ctrl, view)
                        val parameter = ctrl.name.now
                        if (activeObject != null) {
                            ugen.scope(activeObject, parameter)
                        } else {
                            InfoPrompt("Object is not played currently").showDialog(ev)
                        }
                    }
                }
            }

            private fun getActiveObject(ctrl: NamedParameterControl, view: ScoreObjectView?): ActiveObject? {
                val activeObjects = ctrl.parentObject.activeObjects()
                return if (view != null) {
                    activeObjects
                        .filterIsInstance<ActiveScoreObject>()
                        .find { obj ->
                            val absolutePosition = view.absolutePosition + obj.player.pane.absolutePosition
                            obj.absolutePosition == absolutePosition
                        }
                } else activeObjects.singleOrNull()
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
            ): List<ContextualizedAction> = listOf(ServerActions.scopeBus.withContext(control.bus))
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
            ): List<ContextualizedAction> = listOf(
                ServerActions.scopeBus.withContext(control.bus),
                automateWithSynth.withContext(namedControl)
            )

            private val automateWithSynth = action<NamedParameterControl>("Automate with Synth") {
                icon(MaterialDesignS.SINE_WAVE)
                applicableIf { ctrl -> ctrl.parentObject is ScoreObject }
                executes { ctrl, ev ->
                    val obj = ctrl.parentObject as ScoreObject
                    val context = ctrl.context
                    val synthDef = SimpleSearchableRegistryView(context[SynthDefRegistry], "Choose SynthDef")
                        .showPopup(ev, initialOption = null) ?: return@executes
                    val parameter = ctrl.name.now
                    val name = "${obj.name.now}_$parameter"
                    val controls = synthDef.getDefaultControls(null)
                    val outBus = controls.getOrNull("out")?.now
                    if (outBus is BusControl) {
                        outBus.bus.now = (ctrl.now as BusControl).bus.now
                    }
                    val synthObj = SynthObject.create(name, synthDef, controls)
                    synthObj.setInitialSize(obj.duration, height = 0.05.asY)
                    context.compoundEdit("Add automation synth") {
                        for (inst in context[currentProject].mainScore.instancesOf(obj).toList()) {
                            inst.score!!.addObject(synthObj, inst.start, inst.y - 0.06.asY, autoSelect = true)
                        }
                    }
                }
            }
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
            ): List<ContextualizedAction> = listOf(ServerActions.scopeBus.withContext(control.bus))

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
                val displaySwitch = CheckBox()
                    .sync(control.display, description = "Show spectrum", namedControl.context[UndoManager])
                displaySwitch.selectedProperty().bindBidirectional(control.display.asProperty())
                return HBox(
                    5.0,
                    selectorControl,
                    infiniteSpace(),
                    Label("Show spectrum"),
                    displaySwitch
                ).centerChildren()
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

            override fun actions(
                namedControl: NamedParameterControl,
                control: BufferControl,
                view: ScoreObjectView?,
            ): List<ContextualizedAction> = actions.withContext(control)

            private val actions = collectActions<BufferControl> {
                addAction("View contents") {
                    icon(MaterialDesignE.EYE)
                    enableWhen { ctrl -> ctrl.sample.flatMap(BufferReference::isResolved) }
                    executes { ctrl ->
                        when (val buf = ctrl.sample.now.get()) {
                            is SampleObject -> buf.showSpectrogram()
                            is AllocatedBufferObject -> buf.plotBuffer()
                            null -> Logger.warn("Unresolved buffer ${ctrl.sample.now}", Logger.Category.Buffers)
                        }
                    }
                }
            }
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

            override fun actions(
                namedControl: NamedParameterControl,
                control: GlobalPatternControl,
                view: ScoreObjectView?,
            ): List<ContextualizedAction> = actions.withContext(control)

            override fun toString(): String = "Pattern"

            private val actions = collectActions<GlobalPatternControl> {
                addAction("Plot") {
                    icon(MaterialDesignC.CHART_BOX_OUTLINE)
                    enableWhen { ctrl -> ctrl.pattern.flatMap { p -> p.isResolved } }
                    executes { ctrl ->
                        val obj = ctrl.pattern.now.get() ?: return@executes
                        val pane = obj.context[PonticelloMainActivity].patternsPane()
                        pane.showPlotPane(obj)
                    }
                }
                addAction("Edit") {
                    icon(Material2AL.CODE)
                    enableWhen { ctrl -> ctrl.pattern.flatMap(GlobalPatternReference::isResolved) }
                    executes { ctrl ->
                        val obj = ctrl.pattern.now.get() ?: return@executes
                        val pane = obj.context[PonticelloMainActivity].patternsPane()
                        pane.listView.showContent(obj)
                    }
                }
            }
        }

        data object AttackRelease : ControlType<AttackReleaseControl>() {
            override fun createDetailInput(
                namedControl: NamedParameterControl,
                control: AttackReleaseControl,
                view: ScoreObjectView?,
            ): Node {
                val box = HBox(10.0)
                val spec = namedControl.spec.now as? AttackReleaseControlSpec
                val customTotalDuration = spec?.maxDuration ?: reactiveValue(null)
                val objectDuration = namedControl.parentObject.duration()
                val duration = customTotalDuration.orElse(objectDuration ?: reactiveValue(one))
                box.userData = duration.forEach { maxDur ->
                    val timeSpec = NumericalControlSpec(
                        default = zero, min = zero, max = maxDur,
                        step = 0.01.toDecimal(), lag = zero, warp = Warp.Linear, associatedColor = Color.GRAY
                    ).converter(unit = "s")
                    control.attack.now = control.attack.now.coerceAtMost(maxDur)
                    if (objectDuration != null) {
                        control.release.now = control.release.now.coerceAtMost(maxDur - control.attack.now)
                    } else {
                        control.release.now = control.release.now.coerceAtMost(maxDur)
                    }
                    val attack =
                        SliderBar(
                            control.attack, "Attack", timeSpec,
                            undoManager = namedControl.context[UndoManager]
                        )
                    val release = SliderBar(
                        control.release, "Release", timeSpec,
                        undoManager = namedControl.context[UndoManager]
                    )
                    attack.prefWidth = 100.0
                    release.prefWidth = 100.0
                    box.children.setAll(attack, release) //TODO would be awesome to merge them into one slider
                } and control.attack.observe { _, _, attack ->
                    if (objectDuration != null) {
                        control.release.now = control.release.now.coerceAtMost(duration.now - attack)
                    }
                } and control.release.observe { _, _, release ->
                    if (objectDuration != null) {
                        control.attack.now = control.attack.now.coerceAtMost(duration.now - release)
                    }
                }
                return box.centerChildren()
            }

            override fun createDefaultControl(
                obj: ParameterizedObject,
                spec: ControlSpec?,
                oldControl: ParameterControl,
            ): AttackReleaseControl = AttackReleaseControl.createDefault()

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