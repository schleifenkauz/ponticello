package ponticello.ui.score

import bundles.publicProperty
import fxutils.*
import fxutils.controls.IntSpinner
import fxutils.drag.setupDropArea
import fxutils.prompt.SimpleSearchableListView
import fxutils.prompt.compoundPrompt
import hextant.context.Context
import hextant.context.compoundEdit
import hextant.core.editor.defaultState
import hextant.serial.EditorRoot
import javafx.application.Platform
import javafx.collections.FXCollections.observableList
import javafx.geometry.Bounds
import javafx.geometry.Point2D
import javafx.scene.control.ComboBox
import javafx.scene.control.TextField
import javafx.scene.input.MouseButton
import javafx.scene.input.MouseEvent
import javafx.scene.layout.Pane
import javafx.scene.shape.Rectangle
import ponticello.impl.*
import ponticello.model.obj.*
import ponticello.model.player.ScorePlayer
import ponticello.model.registry.*
import ponticello.model.score.*
import ponticello.model.score.controls.BufferControl
import ponticello.model.score.controls.BusControl
import ponticello.model.score.controls.ParameterControl
import ponticello.sc.BufferControlSpec
import ponticello.sc.BusControlSpec
import ponticello.sc.Identifier
import ponticello.sc.defaultControl
import ponticello.sc.editor.CodeBlockEditor
import ponticello.sc.editor.EventDictionaryEditor
import ponticello.ui.controls.DecimalPrompt
import ponticello.ui.controls.NamePrompt
import ponticello.ui.impl.showDialog
import ponticello.ui.registry.SearchableBufferListView
import ponticello.ui.registry.SearchableBusListView
import ponticello.ui.registry.SimpleSearchableRegistryView
import reaktive.value.now
import reaktive.value.reactiveVariable
import java.util.concurrent.CompletableFuture
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.set

abstract class ScorePane(val score: Score, val context: Context) : Pane(), ScoreListener, TimeBlock {
    protected var selectedArea: RectangleSelection? = null
    private var lastMousePress: Point2D? = null

    protected val views = mutableMapOf<ScoreObjectInstance, ScoreObjectView>()
    val allViews: Collection<ScoreObjectView> get() = views.values

    private val selector: ScoreObjectSelectionManager get() = context[ScoreObjectSelectionManager]

    abstract val root: ScorePane

    protected abstract val displayStart: Decimal
    protected abstract val displayEnd: Decimal
    abstract val associatedObject: ScoreObjectGroup?

    abstract val pixelsPerSecond: Double

    init {
        styleClass("score-pane")
    }

    open fun isRoot(obj: ScoreObject) = false

    open fun snapToGrid(position: ObjectPosition): ObjectPosition =
        root.snapToGrid(position + absolutePosition) - absolutePosition

    open fun getNearestGrid(position: ObjectPosition): Pair<Decimal, MeterObject>? =
        root.getNearestGrid(position + absolutePosition)

    fun snapToGrid(x: Double, y: Double): ObjectPosition = snapToGrid(ObjectPosition(getTime(x), getScoreY(y)))

    open fun markT(t: Decimal) {
        val time = t + absolutePosition.time
        root.markT(time)
    }

    override fun getX(time: Decimal): Double = ((time - displayStart) * pixelsPerSecond).toDouble()

    override fun getTime(x: Double): Decimal = (x / pixelsPerSecond) + displayStart

    override fun getDuration(width: Double): Decimal = (width / pixelsPerSecond).asTime

    override fun getWidth(duration: Decimal): Double = (duration * pixelsPerSecond).toDouble()

    abstract fun getScoreY(screenY: Double): Decimal

    abstract fun getScreenY(scoreY: Decimal): Double

    protected open fun addTime(location: Decimal, amount: Decimal) {
        score.addTime(location, amount)
    }

    protected open fun deleteTimeRange(start: Decimal, end: Decimal) {
        score.deleteTimeRange(start, end)
    }

    open fun repaint() {
        layoutObjects(views.iterator(), Long.MAX_VALUE, CompletableFuture())
    }

    protected fun layoutObjects(
        itr: Iterator<Map.Entry<ScoreObjectInstance, ScoreObjectView>>,
        maxTime: Long,
        job: CompletableFuture<Unit>,
    ) {
        val tStart = System.currentTimeMillis()
        while (itr.hasNext()) {
            val (inst, view) = itr.next()
            if (inst.start > displayEnd || inst.start + inst.duration < displayStart) {
                children.remove(view)
                continue
            }
            val resizeHorizontal = !view.prefWidth.isFinite() || view.getDisplayWidth() != view.prefWidth
            val resizeVertical = !view.prefHeight.isFinite() || view.getDisplayHeight() != view.prefHeight
            if (resizeHorizontal || resizeVertical) Platform.runLater {
                view.setPrefSize(view.getDisplayWidth(), view.getDisplayHeight())
                view.rescale()
            }
            view.relocate(getX(inst.start), getScreenY(inst.y))
            if (view !in children) children.add(view)
            if (System.currentTimeMillis() - tStart > maxTime) {
                break
            }
        }
        job.complete(Unit)
    }

    protected open fun listenForEvents() {
        setupDropArea(ScorePaneDropHandler(this))
        addEventHandler(MouseEvent.ANY) { ev ->
            if (ev.target != this) return@addEventHandler
            when (ev.eventType) {
                MouseEvent.MOUSE_PRESSED -> lastMousePress = Point2D(ev.x, ev.y)
                MouseEvent.DRAG_DETECTED -> mouseDragDetected(ev)
                MouseEvent.MOUSE_DRAGGED -> mouseDragged(ev)
                MouseEvent.MOUSE_CLICKED -> {
                    val p = Point2D(ev.x, ev.y)
                    if (lastMousePress != null && p.distance(lastMousePress) > DRAG_THRESH) return@addEventHandler
                    when {
                        ev.button == MouseButton.SECONDARY -> rightClicked(ev)
                        ev.clickCount == 1 -> mouseClicked(ev)
                        ev.clickCount == 2 -> doubleClicked(ev)
                    }
                }

                MouseEvent.MOUSE_RELEASED -> mouseReleased(ev)
            }
        }
    }

    /*
    * Score object view management
    * +*/

    fun getObjectView(inst: ScoreObjectInstance) = views[inst] ?: error("No view found for ${inst.ref.getName()}")

    /*
    * Score change handlers
    * */

    override fun addedObject(score: Score, inst: ScoreObjectInstance, autoSelect: Boolean) {
        val view = createObjectView(inst.obj, inst)
        view.initialize(this)
        view.relocate(getX(inst.start), getScreenY(inst.y))
        view.setPrefSize(view.getDisplayWidth(), view.getDisplayHeight())
        view.rescale()
        views[inst] = view
        children.add(view)
        runFXWithTimeout(10) {
            if (autoSelect) {
                view.selectView(addToSelection = false)
            }
        }
    }

    override fun removedObject(score: Score, inst: ScoreObjectInstance) {
        val view = views.remove(inst) ?: return
        context[ScoreObjectSelectionManager].removed(view)
        children.remove(view)
    }

    /*
    * Mouse events
    * */

    private fun mouseClicked(ev: MouseEvent) {
        val (t, y) = snapToGrid(ev.x, ev.y)
        val duplicator = context[ScoreObjectDuplicator]
        when {
            duplicator.isInDuplicateMode() -> {
                var obj = duplicator.clipboardObject!!
                if (obj.height > score.maxY.now || obj.duration > score.maxTime.now) return
                if (ev.isShiftDown) {
                    val name = context[ScoreObjectRegistry].nameForClone(obj, ev) ?: return
                    obj = obj.clone(name)
                }
                val time = t.coerceIn(zero, score.maxTime.now - obj.duration)
                val scoreY = y.coerceIn(zero, score.maxY.now - obj.height)
                score.addObject(obj, time, scoreY, autoSelect = false)
                ev.consume()
            }

            this is RootScorePane && ev.modifiers.isEmpty() -> {
                ev.consume()
                selector.deselectAll()
                requestFocus()
                val player = context[ScorePlayer.CURRENT]
                if (!player.isScheduled.now) { //TODO pause and replay if currently playing
                    player.playHead.movePlayHead(t)
                }
            }

            else -> {}
        }
    }

    protected open fun rightClicked(ev: MouseEvent) {
        when (ev.modifiers) {
            setOf(Alt) -> {
                val popup = SimpleSearchableRegistryView(context[ScoreObjectRegistry], "Add object instance")
                val anchor = localToScreen(ev.x, ev.y)
                val obj = popup.showPopup(anchor, scene.window) ?: return
                val pos = snapToGrid(ev.x, ev.y)
                val inst = ScoreObjectInstance(obj, pos)
                score.addObject(inst, autoSelect = true)
            }

            setOf(Shift) -> {
                val popup = SimpleSearchableRegistryView(context[BufferRegistry], "Place sample")
                val anchor = localToScreen(ev.x, ev.y)
                val sample = popup.showPopup(anchor, scene.window) ?: return
                val pos = snapToGrid(ev.x, ev.y)
                ScorePaneDropHandler.createPlayBufObject(sample, pos, ev, this)
            }

            setOf(Alt, Shift) -> {
                pasteFromSystemClipboard(ev)
            }
        }
    }

    private fun mouseDragDetected(ev: MouseEvent) {
        clearRegionSelection()
        val pos = snapToGrid(ev.x, ev.y)
        val selectionRect = Rectangle() styleClass "selection-rect"
        val selection = RectangleSelection(this, selectionRect, pos)
        if (ev.modifiers == setOf(Alt, Shift)) {
            selection.useAsTimeSelection()
        }
        children.add(selection.rect)
        selectedArea = selection
    }

    private fun mouseDragged(ev: MouseEvent) {
        if (this is SubScorePane) return
        ev.consume()
        if (selectedArea != null) {
            val pos = snapToGrid(ev.x, ev.y)
            val selection = selectedArea!!
            selection.setOppositeCorner(pos)
            markT(pos.time)
        }
    }

    private fun doubleClicked(ev: MouseEvent) {
        ev.consume()
        val (t, y) = snapToGrid(ev.x, ev.y)
        val type = SimpleSearchableListView(listOf("Task", "Memo"), "Choose object type")
            .showPopup(localToScreen(ev.x, ev.y), scene.window) ?: return
        val obj = when (type) {
            "Task" -> {
                val defaultName = context[ScoreObjectRegistry].availableName("task")
                val name = NamePrompt(context[ScoreObjectRegistry], "Task name", defaultName)
                    .showDialog(ev) ?: return
                val code = EditorRoot(CodeBlockEditor().defaultState())
                TaskObject(code).withName(name)
            }

            "Memo" -> {
                val defaultName = context[ScoreObjectRegistry].availableName("memo")
                MemoObject("").withName(defaultName)
            }

            else -> return
        }

        val inst = ScoreObjectInstance(obj, t, y)
        score.addObject(inst, autoSelect = true)
        if (obj is MemoObject) {
            val view = getObjectView(inst) as MemoObjectView
            runFXWithTimeout {
                view.enterEdit()
            }
        }
    }

    private fun mouseReleased(ev: MouseEvent) {
        ev.consume()
        val selection = selectedArea
        if (selection == null || selection.isEmpty()) return
        clearRegionSelection()
        if (selection.isTimeSelection) {
            selection.rect.requestFocus()
            return
        }
        val containedViews = viewsInside(selection.rect.boundsInParent)
        when (ev.modifiers) {
            setOf(Alt) -> {
                selector.deselectAll()
                if (containedViews.isEmpty()) {
                    addNewObject(selection)
                } else {
                    addNewGroup(ev, selection, containedViews)
                }
            }

            noModifiers, setOf(Shift) -> {
                for ((idx, view) in containedViews.withIndex()) {
                    val addToSelection = idx != 0 || ev.isShiftDown
                    selector.select(view, addToSelection)
                }
            }
        }
    }

    private fun addNewGroup(ev: MouseEvent, selection: RectangleSelection, containedViews: List<ScoreObjectView>) {
        val name = context[ScoreObjectRegistry].nameForGroup(ev) ?: return
        context.compoundEdit("Add object group") {
            val subScore = Score(mutableListOf())
            val groupObj = ScoreObjectGroup(subScore).withName(name)
            val inst = addObject(groupObj, selection)
            val relativePosition = -inst.position
            for (view in containedViews) {
                view.instance.moveInto(subScore, relativePosition, recurse = ev.isShiftDown)
            }
        }
    }

    private fun addNewObject(selection: RectangleSelection) {
        val availableOptions = context[InstrumentRegistry].map(NewObjectOption::Process) +
                context[InstrumentRegistry].map(NewObjectOption::MIDI) +
                context[MeterRegistry].map(NewObjectOption::TempoGrid) +
                listOf(NewObjectOption.Group, NewObjectOption.NewTempoGrid)
        val popup = SimpleSearchableListView(availableOptions, "Add score object")
        val anchor =
            localToScreen(selection.rect.boundsInParent.centerX, selection.rect.boundsInParent.minY)
        val option = popup.showPopup(anchor, scene.window) ?: return
        val obj = if (option is NewObjectOption.MIDI) {
            createMidiObject(option.def, anchor) ?: return
        } else {
            val initialName = option.defaultName(context[ScoreObjectRegistry])
            val name = NamePrompt(context[ScoreObjectRegistry], "Name for object", initialName)
                .showDialog(scene.window, anchor) ?: return
            createNewObject(option, selection.rect, name) ?: return
        }
        addObject(obj, selection)
    }

    fun clearRegionSelection() {
        val selection = selectedArea ?: return
        children.remove(selection.rect)
        selectedArea = null
    }

    private fun pasteFromSystemClipboard(ev: MouseEvent) {
        var instances = context[ScoreObjectSelectionManager].getSystemClipboard() ?: return
        context.compoundEdit("Paste objects") {
            if (ev.isShiftDown) {
                instances = instances.map { inst -> inst.clone() }
            }
            val leftTop = instances.minOf { it.position }
            selector.deselectAll()
            val (t, y) = snapToGrid(ev.x, ev.y)
            for (inst in instances) {
                score.addObject(inst, autoSelect = true)
                inst.moveTo(t - leftTop.time, y - leftTop.y, simpleMove = true)
                selector.select(getObjectView(inst), addToSelection = true)
            }
        }
    }

    private fun createNewObject(option: NewObjectOption, rect: Rectangle, name: String): ScoreObject? {
        return when (option) {
            is NewObjectOption.Process -> {
                val defaultBus = associatedObject?.defaultBusRef?.now?.get()
                val anchor = localToScreen(rect.middlePoint)
                val controls = getInitialControls(option.def, context, defaultBus, anchor) ?: return null
                SoundProcess.create(name, option.def, controls)
            }

            is NewObjectOption.MIDI -> throw AssertionError("Handled before")
            is NewObjectOption.TempoGrid -> TempoGridObject(option.meter.reference()).withName(name)

            NewObjectOption.NewTempoGrid -> {
                val newMeter = MeterObject.create(60, 4, 4).withName(name)
                context[MeterRegistry].add(newMeter)
                TempoGridObject(newMeter.reference()).withName(name)
            }

            NewObjectOption.Group -> ScoreObjectGroup(Score(mutableListOf()))
        }
    }

    private sealed class NewObjectOption {
        override fun toString() = when (this) {
            is Process -> when (def) {
                is SynthDefObject -> "Synth: ${def.name.now}"
                is ProcessDefObject -> "Process: ${def.name.now}"
                else -> throw AssertionError()
            }

            is MIDI -> "MIDI: ${def.name.now}"
            is Group -> "Group"
            is TempoGrid -> "Tempo grid: ${meter.name.now}"
            is NewTempoGrid -> "New tempo grid"
        }

        fun defaultName(registry: ScoreObjectRegistry): String = when (this) {
            is MIDI -> registry.availableName("${def.name.now}_midi")
            is Process -> registry.availableName(def.name.now)
            is TempoGrid -> registry.availableName(meter.name.now)
            is NewTempoGrid -> registry.availableName("tempo")
            Group -> registry.availableName("group")
        }

        class Process(val def: InstrumentObject) : NewObjectOption()
        class MIDI(val def: InstrumentObject) : NewObjectOption()
        class TempoGrid(val meter: MeterObject) : NewObjectOption()
        object Group : NewObjectOption()
        object NewTempoGrid : NewObjectOption()
    }

    private fun showAddTimeDialog(t: Decimal) {
        val amount = DecimalPrompt(
            "How much time to add",
            precision = 2, initialValue = 10.0, 0.0..1000.0
        ).showDialog(context) ?: return
        addTime(t, amount)
    }

    private fun viewsInside(bounds: Bounds) = views.values.filter { v -> bounds.contains(v.boundsInParent) }

    private fun createMidiObject(instr: InstrumentObject, anchor: Point2D): MidiObject? {
        return compoundPrompt("Configure new object") {
            val defaultName = context[ScoreObjectRegistry].availableName("piano_roll")
            val nameField = TextField(defaultName) named "Object name"
            val rootPitchSelector =
                ComboBox(observableList(MidiPitch.allPitchClasses())) named "Root pitch class"
            rootPitchSelector.value = MidiPitch(0)
            val registerSpinner = IntSpinner(0, 10, 4).minColumns(2) named "Base register"
            val octaves = IntSpinner(1, 12, 2).minColumns(2) named "Octaves"
            onConfirm {
                val name = nameField.text
                if (!Identifier.isValid(name) || score.has(name)) return@onConfirm null
                val lowestPitch = rootPitchSelector.value.step + 12 * registerSpinner.value()
                val highestPitch = lowestPitch + 12 * octaves.value()
                val notes = mutableListOf<MidiObject.Note>()
                val eventDictionary = EditorRoot(EventDictionaryEditor())
                MidiObject(
                    reactiveVariable(instr.reference()),
                    lowestPitch,
                    highestPitch,
                    eventDictionary,
                    notes
                ).withName(name)
            }
        }.showDialog(scene.window, anchor)
    }

    private fun addObject(obj: ScoreObject, rect: RectangleSelection): ScoreObjectInstance {
        val registry = context[ScoreObjectRegistry]
        if (obj in registry) registry.add(obj)
        val inst = rect.createInstance(obj)
        obj.liveConfig.yPosition.set(this.absolutePosition.y + inst.y)
        score.addObject(inst, autoSelect = true)
        return inst
    }

    fun removeSelected() {
        val selection = selectedArea
        if (selection != null && selection.isTimeSelection) {
            val start = selection.time
            val end = selection.time + selection.duration
            deleteTimeRange(start, end)
            clearRegionSelection()
        } else {
            selector.removeSelected()
        }
        selector.deselectAll()
    }

    companion object {
        val CURRENT_ROOT = publicProperty<ScorePane>("CurrentRootScorePane")

        fun createObjectView(obj: ScoreObject, instance: ScoreObjectInstance): ScoreObjectView = when (obj) {
            is SoundProcess -> SoundProcessView(obj, instance)
            is TaskObject -> TaskObjectView(obj, instance)
            is MemoObject -> MemoObjectView(obj, instance)
            is ScoreObjectGroup -> ScoreObjectGroupView(obj, instance)
            is MidiObject -> PianoRollObjectView(obj, instance)
            is TempoGridObject -> TempoGridObjectView(obj, instance)
            is ScoreObject.Unresolved -> UnresolvedScoreObjectView(instance)
        }

        private const val DRAG_THRESH = 5.0

        fun getInitialControls(
            def: InstrumentObject, context: Context, defaultBus: BusObject?, anchor: Point2D?,
        ): ParameterControlList? {
            val map = mutableMapOf<String, ParameterControl>()
            for (param in def.parameters) {
                val name = param.name.now
                val control = when (val spec = param.spec.now) {
                    is BufferControlSpec -> {
                        val buffer = SearchableBufferListView(context[BufferRegistry], "Select $name", spec.channels)
                            .showPopup(anchor) ?: return null
                        BufferControl.create(buffer)
                    }

                    is BusControlSpec -> {
                        if (defaultBus != null && defaultBus.matches(spec)) BusControl.create(defaultBus)
                        else {
                            val bus =
                                SearchableBusListView(context[BusRegistry], "Select $name", spec.rate, spec.channels)
                                    .showPopup(anchor) ?: return null
                            BusControl.create(bus)
                        }
                    }

                    else -> spec.defaultControl()
                }
                map[name] = control
            }
            return ParameterControlList.from(map)
        }
    }
}