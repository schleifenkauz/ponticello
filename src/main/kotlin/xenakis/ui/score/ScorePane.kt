package xenakis.ui.score

import fxutils.hasFile
import fxutils.prompt.compoundPrompt
import fxutils.runFXWithTimeout
import fxutils.setupDropArea
import fxutils.styleClass
import hextant.context.Context
import hextant.core.editor.defaultState
import hextant.serial.EditorRoot
import hextant.undo.compoundEdit
import javafx.application.Platform
import javafx.collections.FXCollections.observableList
import javafx.geometry.Bounds
import javafx.scene.control.ComboBox
import javafx.scene.control.Spinner
import javafx.scene.control.TextField
import javafx.scene.input.Dragboard
import javafx.scene.input.MouseButton
import javafx.scene.input.MouseEvent
import javafx.scene.layout.Pane
import javafx.scene.paint.Color.*
import javafx.scene.shape.Rectangle
import reaktive.value.now
import reaktive.value.reactiveVariable
import xenakis.impl.*
import xenakis.impl.Logger.Category
import xenakis.model.obj.BufferObject
import xenakis.model.obj.ParameterizedObjectDef
import xenakis.model.obj.SynthDefObject
import xenakis.model.project.score
import xenakis.model.registry.*
import xenakis.model.score.*
import xenakis.model.score.controls.BufferControl
import xenakis.sc.Identifier
import xenakis.sc.editor.CodeBlockEditor
import xenakis.sc.editor.EventDictionaryEditor
import xenakis.ui.actions.Tool
import xenakis.ui.actions.Tool.*
import xenakis.ui.controls.DecimalPrompt
import xenakis.ui.controls.NamePrompt
import xenakis.ui.impl.rootPane
import xenakis.ui.impl.showDialog
import xenakis.ui.launcher.XenakisLauncher.Companion.currentProject
import xenakis.ui.launcher.XenakisMainActivity
import xenakis.ui.registry.SimpleSearchableRegistryView
import java.util.concurrent.CompletableFuture
import kotlin.math.absoluteValue

abstract class ScorePane(val score: Score, val context: Context) : Pane(), ScoreListener, TimeBlock {
    private var newObject: RectangleSelection? = null
    protected var selectedArea: RectangleSelection? = null

    protected val views = mutableMapOf<ScoreObjectInstance, ScoreObjectView>()
    val allViews: Collection<ScoreObjectView> get() = views.values

    protected val activity get() = context[XenakisMainActivity]
    private val selectedTool get() = activity.toolSelector.selected

    val selector: ScoreObjectSelectionManager get() = context[ScoreObjectSelectionManager]

    protected abstract val displayStart: Decimal
    protected abstract val displayEnd: Decimal
    abstract val associatedObject: ScoreObjectGroup?

    abstract val pixelsPerSecond: Double

    open fun snapToGrid(position: ObjectPosition): ObjectPosition =
        context.rootPane.snapToGrid(position + absolutePosition) - absolutePosition

    open fun getNearestGrid(position: ObjectPosition): ScoreObjectInstance? =
        context.rootPane.getNearestGrid(position + absolutePosition)

    fun snapToGrid(x: Double, y: Double): ObjectPosition = snapToGrid(ObjectPosition(getTime(x), getScoreY(y)))

    open fun markT(t: Decimal) {
        val time = t + absolutePosition.time
        context.rootPane.markT(time)
    }

    override fun getX(time: Decimal): Double = ((time - displayStart) * pixelsPerSecond).toDouble()

    override fun getTime(x: Double): Decimal = (x / pixelsPerSecond) + displayStart

    override fun getDuration(width: Double): Decimal = (width / pixelsPerSecond).asTime

    override fun getWidth(duration: Decimal): Double = (duration * pixelsPerSecond).toDouble()

    open fun getScoreY(screenY: Double): Decimal = (screenY / context.rootPane.height).asY

    open fun getScreenY(scoreY: Decimal): Double = (scoreY * context.rootPane.height).toDouble()

    init {
        styleClass("score-pane")
    }

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
            val pane = when (val target = ev.target) {
                is ScorePane -> target
                is ScoreObjectGroupView -> if (target.isInitialized) target.scorePane else return@addEventHandler
                else -> return@addEventHandler
            }
            val e = ev.copyFor(pane, pane)
            when (ev.eventType) {
                MouseEvent.MOUSE_PRESSED -> pane.mousePressed(e)
                MouseEvent.MOUSE_DRAGGED -> pane.mouseDragged(e)
                MouseEvent.MOUSE_RELEASED -> pane.mouseReleased(e)
            }
        }
        setOnMouseClicked { ev ->
            if (ev.clickCount >= 2) {
                doubleClicked(ev)
            }

        }
        addPlayBufOnDrop()
    }

    private fun addPlayBufOnDrop() {
        setupDropArea({ db -> db.hasFile("wav") || db.hasContent(BufferObject.DATA_FORMAT) }, { ev ->
            val sample = extractBufferFromDragboard(ev.dragboard) ?: return@setupDropArea
            val pos = snapToGrid(ev.x, ev.y)
            createPlayBufObject(sample, pos)
        })
    }

    private fun extractBufferFromDragboard(db: Dragboard): BufferObject? = when {
        db.hasFiles() -> {
            val file = db.files[0]
            context[BufferRegistry].getOrAdd(file)
        }

        db.hasContent(BufferObject.DATA_FORMAT) -> {
            context[BufferRegistry].get(db.getContent(BufferObject.DATA_FORMAT) as String)
        }

        else -> null
    }

    private fun createPlayBufObject(buffer: BufferObject, pos: ObjectPosition) =
        context.compoundEdit("Add sample to score") {
            val instruments = context[SynthDefRegistry]
            val synthDef = instruments.selectedInstrument
            if (synthDef !is SynthDefObject) {
                Logger.error("A SynthDef should be selected for sample playback to work", Category.Buffers)
                return
            }
            val controls = getDefaultControls(synthDef)
            val synthDefRef = reactiveVariable(synthDef.reference())
            val name = context[ScoreObjectRegistry].availableName(buffer.name.now)
            val obj = SynthObject(reactiveVariable(name), synthDefRef, controls)
            obj.setInitialSize(buffer.duration().now, 0.02.withPrecision(ObjectPosition.Y_PRECISION))
            val inst = ScoreObjectInstance(obj, pos)
            score.addObject(inst)
            controls.reassignControl("buf", BufferControl(reactiveVariable(buffer.reference())))
        }

    private fun getDefaultControls(def: ParameterizedObjectDef): ParameterControlList {
        val defaultGroup = associatedObject?.defaultGroupRef?.now ?: context[GroupRegistry].getDefault().reference()
        val defaultBus = associatedObject?.defaultBusRef?.now ?: context[BusRegistry].getDefault().reference()
        val controls = def.defaultControls(context, defaultGroup, defaultBus)
        return ParameterControlList.from(controls)
    }

    /*
    * Score object view management
    * +*/

    fun getObjectView(inst: ScoreObjectInstance) = views[inst] ?: error("No view found for ${inst.ref.getName()}")

    fun createObjectView(inst: ScoreObjectInstance): ScoreObjectView = when (val obj = inst.obj) {
        is SynthObject -> SynthObjectView(inst, obj)
        is TaskObject -> TaskObjectView(inst, obj)
        is ProcessObject -> ProcessObjectView(inst, obj)
        is MemoObject -> MemoObjectView(inst, obj)
        is ScoreObjectGroup -> ScoreObjectGroupView(inst, obj)
        is PianoRollObject -> PianoRollObjectView(inst, obj)
        is TempoGridObject -> TempoGridObjectView(inst, obj)
        is ScoreObject.Unresolved -> UnresolvedScoreObjectView(inst)
    }

    /*
    * Score change handlers
    * */

    override fun addedObject(score: Score, inst: ScoreObjectInstance) {
        val view = createObjectView(inst)
        view.initialize(this)
        views[inst] = view
        children.add(view)
        Platform.runLater {
            view.requestFocus()
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

    private fun mousePressed(ev: MouseEvent) {
        ev.consume()
        val selectedTool = activity.toolSelector.selected
        if (newObject != null) return
        clearRegionSelection()
        val pos = snapToGrid(ev.x, ev.y)
        val (t, y) = pos
        val rect = Rectangle(getX(t), getScreenY(y), 0.0, 0.0)
        when (selectedTool) {
            Synth -> {
                val synthDef = context[SynthDefRegistry].selectedInstrument
                if (synthDef !is SynthDefObject) return
                rect.fill = synthDef.color.now
            }

            Process -> {
                val processDef = context[ProcessDefRegistry].selectedDef ?: return
                rect.fill = processDef.color.now
            }

            Task, Memo, Cut, AddTime -> return

            Group -> {
                rect.stroke = WHITE
                rect.fill = rgb(0, 0, 0, 0.3)
            }

            PianoRoll -> rect.fill = context[SynthDefRegistry].selectedInstrument?.color?.now ?: return

            TempoGrid -> {
                rect.fill = TRANSPARENT
                rect.stroke = BLACK
            }

            Pointer, Resize -> {
                startSelection(pos, ev)
                return
            }
        }
        beginNewObject(pos, rect)
    }

    protected open fun startSelection(pos: ObjectPosition, ev: MouseEvent) {
        check(selectedArea == null)
        val selectionRect = Rectangle() styleClass "selection-rect"
        val selection = RectangleSelection(this, selectionRect, pos)
        if (ev.isControlDown) {
            selection.useAsTimeSelection()
        }
        children.add(selection.rect)
        selectedArea = selection
    }

    protected fun clearRegionSelection() {
        val selection = selectedArea ?: return
        children.remove(selection.rect)
        selectedArea = null
    }

    private fun mouseDragged(ev: MouseEvent) {
        val newObj = newObject
        val pos = snapToGrid(ev.x, ev.y)
        when {
            newObj != null -> {
                newObj.setOppositeCorner(pos)
                markT(pos.time)
                ev.consume()
            }

            selectedArea != null && canSelectRegion() -> {
                val selection = selectedArea!!
                selection.setOppositeCorner(pos)
                markT(pos.time)
            }
        }
    }

    private fun canSelectRegion() =
        (this is ScoreView && selectedTool in setOf(Pointer, Resize)
                || this is SubScorePane && selectedTool == Group)

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
                score.addObject(inst)
                inst.moveTo(t - leftTop.time, y - leftTop.y, simpleMove = true)
                selector.select(getObjectView(inst), addToSelection = true)
            }
        }
    }

    private fun doubleClicked(ev: MouseEvent) {
        val tool = activity.toolSelector.selected
        if (tool in setOf(Task, Memo)) {
            ev.consume()
            val (t, y) = snapToGrid(ev.x, ev.y)
            val obj = when (tool) {
                Task -> {
                    val defaultName = context[ScoreObjectRegistry].availableName("task")
                    val name = NamePrompt(context[ScoreObjectRegistry], "Task name", defaultName)
                        .showDialog(context) ?: return
                    val code = EditorRoot(CodeBlockEditor().defaultState())
                    TaskObject(reactiveVariable(name), code)
                }

                Memo -> {
                    val defaultName = context[ScoreObjectRegistry].availableName("memo")
                    MemoObject(reactiveVariable(defaultName), "")
                }

                else -> throw AssertionError()
            }
            val inst = ScoreObjectInstance(obj, t, y)
            score.addObject(inst)
            if (obj is MemoObject) {
                val view = getObjectView(inst) as MemoObjectView
                runFXWithTimeout {
                    view.enterEdit()
                }
            }
        }
    }

    private fun mouseReleased(ev: MouseEvent) {
        if (ev.target != this) return
        ev.consume()
        if (score == context[currentProject].score && !ev.isShiftDown) selector.deselectAll()
        val newObj = newObject
        val selection = selectedArea
        val tool = activity.toolSelector.selected
        val (t, y) = snapToGrid(ev.x, ev.y)
        val scoreView = context[XenakisMainActivity].scoreView
        when {
            tool == AddTime -> {
                val amount = DecimalPrompt(
                    "How much time to add",
                    precision = 2, initialValue = 10.0, 0.0..1000.0
                ).showDialog(context) ?: return
                addTime(t, amount)
            }

            newObj != null -> {
                if (newObj.isNotEmpty()) {
                    createNewObject(tool, newObj, ev)
                }
                endNewObject()
            }

            ev.button == MouseButton.PRIMARY && ev.isAltDown && ev.isShiftDown -> {
                val popup = SimpleSearchableRegistryView(context[ScoreObjectRegistry], "Add object instance")
                val anchor = localToScreen(ev.x, ev.y)
                val obj = popup.showPopup(anchor, scene.window) ?: return
                val pos = snapToGrid(ev.x, ev.y)
                val inst = ScoreObjectInstance(obj, pos)
                score.addObject(inst)
            }

            ev.button == MouseButton.PRIMARY && ev.isAltDown -> {
                val popup = SimpleSearchableRegistryView(context[BufferRegistry], "Place sample")
                val anchor = localToScreen(ev.x, ev.y)
                val sample = popup.showPopup(anchor, scene.window) ?: return
                val pos = snapToGrid(ev.x, ev.y)
                createPlayBufObject(sample, pos)
            }

            ev.button == MouseButton.PRIMARY && scoreView.isInDuplicateMode() -> {
                var obj = scoreView.clipboardObject!!
                if (obj.height > score.maxY || obj.duration > score.maxTime) return
                if (ev.isShiftDown) {
                    val name = context[ScoreObjectRegistry].nameForClone(obj)
                    obj = obj.clone(name)
                }
                val time = t.coerceIn(zero, score.maxTime - obj.duration)
                val scoreY = y.coerceIn(zero, score.maxY - obj.height)
                val duplicate = ScoreObjectInstance(obj, time, scoreY)
                score.addObject(duplicate)
            }

            selection != null && selection.isNotEmpty() -> {
                if (!ev.isShiftDown) {
                    selector.deselectAll()
                }
                if (selection.isTimeSelection) {
                    selection.rect.requestFocus()
                } else {
                    val views = viewsInside(selection.rect.boundsInParent)
                    for (view in views) {
                        selector.select(view, addToSelection = true)
                    }
                    clearRegionSelection()
                }
            }

            ev.button == MouseButton.SECONDARY -> pasteFromSystemClipboard(ev)

            this is ScoreView && !activity.playback.player.isPlaying.now -> {
                if (ev.isControlDown) {
                    activity.playback.attachToMainScore()
                }
                if (activity.playback.isAttachedTo(this)) {
                    activity.playback.playHead.movePlayHead(t)
                }
            }

            this is ScoreView -> {
                if (!ev.isShiftDown) {
                    context[ScoreObjectSelectionManager].deselectAll()
                    requestFocus()
                }
            }
        }
    }

    private fun viewsInside(bounds: Bounds) = views.values.filter { v -> bounds.contains(v.boundsInParent) }

    /*
    * Object creation
    * */

    private fun createNewObject(tool: Tool, rect: RectangleSelection, ev: MouseEvent) {
        try {
            val obj = when (tool) {
                Synth -> {
                    val def = context[SynthDefRegistry].selectedInstrument
                    if (def !is SynthDefObject) return
                    val initialName = context[ScoreObjectRegistry].availableName(def.name.now)
                    val name = NamePrompt(context[ScoreObjectRegistry], "Name for new Synth object", initialName)
                        .showDialog(context) ?: return
                    val ref = reactiveVariable(def.reference())
                    val controls = getDefaultControls(def)
                    SynthObject(reactiveVariable(name), ref, controls)
                }

                Process -> {
                    val def = context[ProcessDefRegistry].selectedDef ?: return
                    val initialName = context[ScoreObjectRegistry].availableName(def.name.now)
                    val name = NamePrompt(context[ScoreObjectRegistry], "Name for new Process object", initialName)
                        .showDialog(context) ?: return
                    val ref = reactiveVariable(def.reference())
                    val controls = getDefaultControls(def)
                    ProcessObject(reactiveVariable(name), ref, controls)
                }

                Group -> {
                    val name = context[ScoreObjectRegistry].availableName("group")
                    context.compoundEdit("Add object group") {
                        val subScore = Score(mutableListOf())
                        val groupObj = ScoreObjectGroup(reactiveVariable(name), subScore)
                        val inst = addObject(groupObj, rect)
                        val relativePosition = -inst.position
                        for (view in viewsInside(rect.rect.boundsInParent)) {
                            view.instance.moveInto(subScore, relativePosition, recurse = ev.isShiftDown)
                        }
                    }
                    return
                }

                PianoRoll -> {
                    val instr = context[SynthDefRegistry].selectedInstrument ?: return
                    compoundPrompt("Configure new object") {
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
                            val notes = mutableListOf<PianoRollObject.Note>()
                            val eventDictionary = EditorRoot(EventDictionaryEditor())
                            PianoRollObject(
                                reactiveVariable(name),
                                reactiveVariable(instr.reference()),
                                lowestPitch,
                                highestPitch,
                                eventDictionary,
                                notes
                            )
                        }
                    }.showDialog(context) ?: return
                }

                TempoGrid -> {
                    val name = context[ScoreObjectRegistry].availableName("grid")
                    TempoGridObject.createDefault(name)
                }

                Pointer, Cut, AddTime, Task, Memo, Resize -> {
                    Logger.warn("Unrecognized object type $tool", Category.Score)
                    return
                }
            }
            addObject(obj, rect)
        } catch (e: Exception) {
            Logger.error("Error creating new object", e, Category.Score)
        }
    }

    private fun addObject(obj: ScoreObject, rect: RectangleSelection): ScoreObjectInstance {
        val registry = context[ScoreObjectRegistry]
        if (obj in registry) registry.add(obj)
        val inst = rect.createInstance(obj)
        score.addObject(inst)
        selector.select(getObjectView(inst), addToSelection = false)
        return inst
    }

    private fun beginNewObject(position: ObjectPosition, s: Rectangle) {
        if (s !in children) children.add(s)
        newObject = RectangleSelection(this, s, position)
    }

    fun endNewObject() {
        if (newObject != null) {
            children.remove(newObject!!.rect)
            newObject = null
        }
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
}