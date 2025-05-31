package ponticello.ui.score

import bundles.publicProperty
import fxutils.*
import fxutils.prompt.SimpleSearchableListView
import fxutils.prompt.compoundPrompt
import hextant.context.Context
import hextant.context.compoundEdit
import hextant.core.editor.defaultState
import hextant.serial.EditorRoot
import javafx.application.Platform
import javafx.collections.FXCollections.observableList
import javafx.event.Event
import javafx.geometry.Bounds
import javafx.geometry.Point2D
import javafx.scene.control.ComboBox
import javafx.scene.control.Spinner
import javafx.scene.control.TextField
import javafx.scene.input.Dragboard
import javafx.scene.input.MouseButton
import javafx.scene.input.MouseEvent
import javafx.scene.layout.Pane
import javafx.scene.shape.Rectangle
import ponticello.impl.*
import ponticello.model.obj.BufferObject
import ponticello.model.obj.MeterObject
import ponticello.model.obj.ProcessDefObject
import ponticello.model.obj.SynthDefObject
import ponticello.model.player.ScorePlayer
import ponticello.model.project.UI_STATE
import ponticello.model.project.get
import ponticello.model.registry.*
import ponticello.model.score.*
import ponticello.sc.Identifier
import ponticello.sc.editor.CodeBlockEditor
import ponticello.sc.editor.EventDictionaryEditor
import ponticello.ui.controls.DecimalPrompt
import ponticello.ui.controls.NamePrompt
import ponticello.ui.impl.showDialog
import ponticello.ui.launcher.PonticelloLauncher.Companion.currentProject
import ponticello.ui.launcher.PonticelloMainActivity
import ponticello.ui.registry.SimpleSearchableRegistryView
import reaktive.value.now
import reaktive.value.reactiveVariable
import java.util.concurrent.CompletableFuture
import kotlin.math.absoluteValue

abstract class ScorePane(val score: Score, val context: Context) : Pane(), ScoreListener, TimeBlock {
    protected var selectedArea: RectangleSelection? = null
    private var lastMousePress: Point2D? = null

    protected val views = mutableMapOf<ScoreObjectInstance, ScoreObjectView>()
    val allViews: Collection<ScoreObjectView> get() = views.values

    protected val activity get() = context[PonticelloMainActivity]

    private val selector: ScoreObjectSelectionManager get() = context[ScoreObjectSelectionManager]

    abstract val root: ScorePane

    protected abstract val displayStart: Decimal
    protected abstract val displayEnd: Decimal
    abstract val associatedObject: ScoreObjectGroup?

    abstract val pixelsPerSecond: Double

    init {
        styleClass("score-pane")
        heightProperty().addListener { _ -> repaint() }
        widthProperty().addListener { _ -> repaint() }
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

    open fun getScoreY(screenY: Double): Decimal = (screenY / root.height).asY

    open fun getScreenY(scoreY: Decimal): Double = (scoreY * root.height).toDouble()

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
            val resizeHorizontal = (view.getDisplayWidth() - view.prefWidth).absoluteValue > 0.01
            val resizeVertical = (view.getDisplayHeight() - view.prefHeight).absoluteValue > 0.01
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
        addEventHandler(MouseEvent.ANY) { ev ->
            if (ev.target != this) return@addEventHandler
            if (this !is RootScorePane && !ev.isControlDown) return@addEventHandler
            ev.consume()
            when (ev.eventType) {
                MouseEvent.MOUSE_PRESSED -> mousePressed(ev)
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
        addPlayBufOnDrop()
    }

    private fun addPlayBufOnDrop() {
        setupDropArea({ db -> db.hasFile("wav") || db.hasContent(BufferObject.DATA_FORMAT) }, { ev ->
            val sample = extractBufferFromDragboard(ev.dragboard) ?: return@setupDropArea
            val pos = snapToGrid(ev.x, ev.y)
            createPlayBufObject(sample, pos, ev)
        })
    }

    private fun extractBufferFromDragboard(db: Dragboard): BufferObject? = when {
        db.hasFiles() -> {
            val file = db.files[0]
            context[BufferRegistry].getOrAdd(file)
        }
        db.hasContent(BufferObject.DATA_FORMAT) -> {
            val bufName = db.getContent(BufferObject.DATA_FORMAT) as String
            context[BufferRegistry].get(bufName)
        }

        else -> null
    }

    private fun createPlayBufObject(buffer: BufferObject, position: ObjectPosition, ev: Event?) {
        val synthDef = buffer.context[currentProject][UI_STATE].getOrSelectSynthDef(ev) ?: return
        val obj = buffer.createSynthObject(synthDef) ?: return
        val inst = ScoreObjectInstance(obj, position)
        context.compoundEdit("Add sample to score") {
            score.addObject(inst, autoSelect = true)

        }
    }

    /*
    * Score object view management
    * +*/

    fun getObjectView(inst: ScoreObjectInstance) = views[inst] ?: error("No view found for ${inst.ref.getName()}")

    /*
    * Score change handlers
    * */

    override fun addedObject(score: Score, inst: ScoreObjectInstance, autoSelect: Boolean)  {
        val view = createObjectView(inst.obj, inst)
        view.initialize(this)
        views[inst] = view
        children.add(view)
        if (autoSelect) {
            Platform.runLater {
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
                createPlayBufObject(sample, pos, ev)
            }

            setOf(Alt, Shift) -> {
                pasteFromSystemClipboard(ev)
            }
        }
    }

    private fun mouseClicked(ev: MouseEvent) {
        val (t, y) = snapToGrid(ev.x, ev.y)
        val duplicator = context[ScoreObjectDuplicator]
        when {
            duplicator.isInDuplicateMode() -> {
                var obj = duplicator.clipboardObject!!
                if (obj.height > score.maxY.now || obj.duration > score.maxTime.now) return
                if (ev.isAltDown) {
                    val name = context[ScoreObjectRegistry].nameForClone(obj)
                    obj = obj.clone(name)
                }
                val time = t.coerceIn(zero, score.maxTime.now - obj.duration)
                val scoreY = y.coerceIn(zero, score.maxY.now - obj.height)
                score.addObject(obj, time, scoreY, autoSelect = false)
            }

            this is RootScorePane && ev.modifiers.isEmpty() -> {
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

    private fun mousePressed(ev: MouseEvent) {
        lastMousePress = Point2D(ev.x, ev.y)
        clearRegionSelection()
        val pos = snapToGrid(ev.x, ev.y)
        val selectionRect = Rectangle() styleClass "selection-rect"
        val selection = RectangleSelection(this, selectionRect, pos)
        if (ev.modifiers == setOf(Shift, Alt)) {
            selection.useAsTimeSelection()
        }
        children.add(selection.rect)
        selectedArea = selection
    }

    fun clearRegionSelection() {
        val selection = selectedArea ?: return
        children.remove(selection.rect)
        selectedArea = null
    }

    private fun mouseDragged(ev: MouseEvent) {
        if (selectedArea != null) {
            val pos = snapToGrid(ev.x, ev.y)
            val selection = selectedArea!!
            selection.setOppositeCorner(pos)
            markT(pos.time)
        }
    }

    private fun doubleClicked(ev: MouseEvent) {
        val (t, y) = snapToGrid(ev.x, ev.y)
        val type = SimpleSearchableListView(listOf("Task", "Memo"), "Choose object type")
            .showPopup(localToScreen(ev.x, ev.y), scene.window) ?: return
        val obj = when (type) {
            "Task" -> {
                val defaultName = context[ScoreObjectRegistry].availableName("task")
                val name = NamePrompt(context[ScoreObjectRegistry], "Task name", defaultName)
                    .showDialog(ev) ?: return
                val code = EditorRoot(CodeBlockEditor().defaultState())
                TaskObject(reactiveVariable(name), code)
            }

            "Memo" -> {
                val defaultName = context[ScoreObjectRegistry].availableName("memo")
                MemoObject(reactiveVariable(defaultName), "")
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
                    val availableOptions = context[SynthDefRegistry].map(NewObjectOption::Synth) +
                            context[ProcessDefRegistry].map(NewObjectOption::Process) +
                            context[SynthDefRegistry].map(NewObjectOption::MIDI) +
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
                        createNewObject(option, name)
                    }
                    addObject(obj, selection)
                } else {
                    val name = context[ScoreObjectRegistry].availableName("group")
                    context.compoundEdit("Add object group") {
                        val subScore = Score(mutableListOf())
                        val groupObj = ScoreObjectGroup(reactiveVariable(name), subScore)
                        val inst = addObject(groupObj, selection)
                        val relativePosition = -inst.position
                        for (view in containedViews) {
                            view.instance.moveInto(subScore, relativePosition, recurse = ev.isShiftDown)
                        }
                    }
                    return
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

    private fun createNewObject(option: NewObjectOption, name: String) = when (option) {
        is NewObjectOption.Synth -> {
            val controls = option.def.getDefaultControls(associatedObject)
            SynthObject.create(name, option.def, controls)
        }

        is NewObjectOption.Process -> {
            val controls = option.def.getDefaultControls(associatedObject)
            ProcessObject(
                reactiveVariable(name),
                reactiveVariable(option.def.reference()),
                controls
            )
        }

        is NewObjectOption.MIDI -> throw AssertionError("Handled before")
        is NewObjectOption.TempoGrid -> TempoGridObject(
            reactiveVariable(name),
            option.meter.reference()
        )

        NewObjectOption.NewTempoGrid -> {
            val newMeter = MeterObject.create(name, 60, 4, 4)
            context[MeterRegistry].add(newMeter)
            TempoGridObject(reactiveVariable(name), newMeter.reference())
        }

        NewObjectOption.Group -> ScoreObjectGroup(reactiveVariable(name), Score(mutableListOf()))
    }

    private sealed class NewObjectOption {
        override fun toString() = when (this) {
            is Synth -> "Synth: ${def.name.now}"
            is Process -> "Process: ${def.name.now}"
            is MIDI -> "MIDI: ${def.name.now}"
            is Group -> "Group"
            is TempoGrid -> "Tempo grid: ${meter.name.now}"
            is NewTempoGrid -> "New tempo grid"
        }

        fun defaultName(registry: ScoreObjectRegistry): String = when (this) {
            is Synth -> registry.availableName(def.name.now)
            is MIDI -> registry.availableName("${def.name.now}_midi")
            is Process -> registry.availableName(def.name.now)
            is TempoGrid -> registry.availableName(meter.name.now)
            is NewTempoGrid -> registry.availableName("tempo")
            Group -> registry.availableName("group")
        }

        class Synth(val def: SynthDefObject) : NewObjectOption()
        class Process(val def: ProcessDefObject) : NewObjectOption()
        class MIDI(val def: SynthDefObject) : NewObjectOption()
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

    private fun createMidiObject(instr: SynthDefObject, anchor: Point2D): MidiObject? {
        return compoundPrompt("Configure new object") {
            val defaultName = context[ScoreObjectRegistry].availableName("piano_roll")
            val nameField = TextField(defaultName) named "Object name"
            val rootPitchSelector =
                ComboBox(observableList(MidiPitch.allPitchClasses())) named "Root pitch class"
            rootPitchSelector.value = MidiPitch(0)
            val registerSpinner = Spinner<Int>(0, 10, 4) named "Base register"
            val octaves = Spinner<Int>(1, 12, 2) named "Octaves"
            onConfirm {
                val name = nameField.text
                if (!Identifier.isValid(name) || score.has(name)) return@onConfirm null
                val lowestPitch = rootPitchSelector.value.step + 12 * registerSpinner.value
                val highestPitch = lowestPitch + 12 * octaves.value
                val notes = mutableListOf<MidiObject.Note>()
                val eventDictionary = EditorRoot(EventDictionaryEditor())
                MidiObject(
                    reactiveVariable(name),
                    reactiveVariable(instr.reference()),
                    lowestPitch,
                    highestPitch,
                    eventDictionary,
                    notes
                )
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
            is SynthObject -> SynthObjectView(obj, instance)
            is TaskObject -> TaskObjectView(obj, instance)
            is ProcessObject -> ProcessObjectView(obj, instance)
            is MemoObject -> MemoObjectView(obj, instance)
            is ScoreObjectGroup -> ScoreObjectGroupView(obj, instance)
            is MidiObject -> PianoRollObjectView(obj, instance)
            is TempoGridObject -> TempoGridObjectView(obj, instance)
            is ScoreObject.Unresolved -> UnresolvedScoreObjectView(instance)
        }

        private const val DRAG_THRESH = 5.0
    }
}