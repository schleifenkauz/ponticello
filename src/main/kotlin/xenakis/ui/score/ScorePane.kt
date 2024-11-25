package xenakis.ui.score

import hextant.context.Context
import hextant.fx.runFXWithTimeout
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
import xenakis.model.Logger
import xenakis.model.Logger.Category
import xenakis.model.obj.ParameterizedObjectDef
import xenakis.model.obj.SampleObject
import xenakis.model.obj.SynthDefObject
import xenakis.model.registry.InstrumentRegistry
import xenakis.model.registry.ProcessDefRegistry
import xenakis.model.registry.SampleRegistry
import xenakis.model.registry.ScoreObjectRegistry
import xenakis.model.score.*
import xenakis.model.score.Score.Companion.rootScore
import xenakis.sc.Identifier
import xenakis.sc.editor.EventDictionaryEditor
import xenakis.sc.editor.ScFunctionEditor
import xenakis.ui.ToolSelector.Tool
import xenakis.ui.ToolSelector.Tool.*
import xenakis.ui.XenakisUI
import xenakis.ui.impl.hasFile
import xenakis.ui.impl.rootPane
import xenakis.ui.impl.setupDropArea
import xenakis.ui.impl.styleClass
import xenakis.ui.prompt.DecimalPrompt
import xenakis.ui.prompt.NamePrompt
import xenakis.ui.prompt.compoundInput
import xenakis.ui.registry.SimpleSearchableRegistryView

abstract class ScorePane(val score: Score, val context: Context) : Pane(), ScoreListener, TimeBlock {
    private var newObject: RectangleSelection? = null
    protected var selectedArea: RectangleSelection? = null

    private val views = mutableMapOf<ScoreObjectInstance, ScoreObjectView>()
    val allViews: Collection<ScoreObjectView> get() = views.values

    protected val ui get() = context[XenakisUI]
    private val selectedTool get() = ui.toolSelector.selected.value!!

    val selector: ScoreObjectSelectionManager get() = context[ScoreObjectSelectionManager]

    protected abstract val displayStart: Decimal
    protected abstract val displayEnd: Decimal
    abstract val associatedObject: ScoreObjectGroup?

    open fun snapToGrid(position: ObjectPosition): ObjectPosition =
        context.rootPane.snapToGrid(position + absolutePosition) - absolutePosition

    open fun getNearestGrid(position: ObjectPosition): ScoreObjectInstance? =
        context.rootPane.getNearestGrid(position + absolutePosition)

    fun snapToGrid(x: Double, y: Double): ObjectPosition = snapToGrid(ObjectPosition(getTime(x), getScoreY(y)))

    open fun markT(t: Decimal) {
        val time = t + absolutePosition.time
        context.rootPane.markT(time)
    }

    override fun getX(time: Decimal): Double = ((time - displayStart) * context.rootPane.pixelsPerSecond).toDouble()

    override fun getTime(x: Double): Decimal = (x / context.rootPane.pixelsPerSecond) + displayStart

    override fun getDuration(width: Double): Decimal =
        (width / context.rootPane.pixelsPerSecond).asTime

    override fun getWidth(duration: Decimal): Double = (duration * context.rootPane.pixelsPerSecond).toDouble()

    fun getScoreY(paneY: Double): Decimal = (paneY / context.rootPane.height).asY
    fun getPaneY(scoreY: Decimal): Double = (scoreY * context.rootPane.height).toDouble()

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
        children.clear()
        layoutObjects()
    }

    private fun layoutObjects() {
        for ((inst, view) in views) {
            if (inst.start > displayEnd) continue
            if (inst.start + inst.duration < displayStart) continue
            if (view.getDisplayWidth() != view.prefWidth || view.getDisplayHeight() != view.prefHeight) {
                view.setPrefSize(view.getDisplayWidth(), view.getDisplayHeight())
                view.rescale()
            }
            view.relocate(getX(inst.start), getPaneY(inst.y))
            children.add(view)
        }
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
        addPlayBufOnDrop()
    }

    private fun addPlayBufOnDrop() {
        setupDropArea({ db -> db.hasFile("wav") || db.hasContent(SampleObject.DATA_FORMAT) }, { ev ->
            val sample = extractSampleFromDragBoard(ev.dragboard) ?: return@setupDropArea
            val pos = snapToGrid(ev.x, ev.y)
            createPlayBufObject(sample, pos)
        })
    }

    private fun extractSampleFromDragBoard(db: Dragboard): SampleObject? = when {
        db.hasFiles() -> {
            val file = db.files[0]
            context[SampleRegistry].getOrAdd(file)
        }

        db.hasContent(SampleObject.DATA_FORMAT) -> {
            context[SampleRegistry].get(db.getContent(SampleObject.DATA_FORMAT) as String)
        }

        else -> null
    }

    private fun createPlayBufObject(sample: SampleObject, pos: ObjectPosition) =
        context.compoundEdit("Add sample to score") {
            val instruments = context[InstrumentRegistry]
            val synthDef = instruments.selectedInstrument.now
            if (synthDef !is SynthDefObject) {
                Logger.error("A SynthDef should be selected for sample playback to work", Category.Buffers)
                return
            }
            val controls = getDefaultControls(synthDef)
            val synthDefRef = reactiveVariable(synthDef.createReference())
            val name = context[ScoreObjectRegistry].availableName(sample.name.now)
            val obj = SynthObject(reactiveVariable(name), synthDefRef, controls)
            obj.setInitialSize(sample.duration, 0.02.withPrecision(ObjectPosition.Y_PRECISION))
            val inst = ScoreObjectInstance(obj, pos)
            score.addObject(inst)
            controls.reassignControl("buf", BufferControl(reactiveVariable(sample.createReference())))
        }

    private fun getDefaultControls(def: ParameterizedObjectDef): ParameterControls {
        val defaultGroup = associatedObject?.defaultGroupRef?.now
        val defaultBus = associatedObject?.defaultBusRef?.now
        val controls = def.defaultControls(context, defaultGroup, defaultBus)
        return controls
    }

    /*
    * Score object view management
    * +*/

    fun getObjectView(inst: ScoreObjectInstance) = views[inst] ?: error("No view found for ${inst.obj.name.now}")

    private fun createObjectView(inst: ScoreObjectInstance): ScoreObjectView = when (val obj = inst.obj) {
        is SynthObject -> SynthObjectView(inst, obj)
        is TaskObject -> TaskObjectView(inst, obj)
        is ProcessObject -> ProcessObjectView(inst, obj)
        is MemoObject -> MemoObjectView(inst, obj)
        is ScoreObjectGroup -> ScoreObjectGroupView(inst, obj)
        is PianoRollObject -> PianoRollObjectView(inst, obj)
        is TempoGridObject -> TempoGridObjectView(inst, obj)
        is ScoreObject.Unresolved -> UnresolvedScoreObjectView(obj, inst)
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
        val selectedTool = ui.toolSelector.selected.value!!
        if (newObject != null) return
        clearRegionSelection()
        val pos = snapToGrid(ev.x, ev.y)
        val (t, y) = pos
        val rect = Rectangle(getX(t), getPaneY(y), 0.0, 0.0)
        when (selectedTool) {
            Synth -> {
                val synthDef = context[InstrumentRegistry].selectedInstrument.now
                if (synthDef !is SynthDefObject) return
                rect.fill = synthDef.color.now
            }

            Process -> {
                val processDef = context[ProcessDefRegistry].selectedDef ?: return
                rect.fill = processDef.color.now
            }

            Task, Memo, Cut, AddTime, Resize -> return

            Group -> {
                rect.stroke = WHITE
                rect.fill = rgb(0, 0, 0, 0.3)
            }

            PianoRoll -> rect.fill = context[InstrumentRegistry].selectedInstrument.now?.color?.now ?: return

            TempoGrid -> {
                rect.fill = TRANSPARENT
                rect.stroke = BLACK
            }

            Pointer -> {
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

    private fun clearRegionSelection() {
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

    private fun mouseReleased(ev: MouseEvent) {
        if (ev.target != this) return
        ev.consume()
        if (score == context[rootScore] && !ev.isShiftDown) selector.deselectAll()
        val newObj = newObject
        val selection = selectedArea
        val tool = ui.toolSelector.selected.value
        val (t, y) = snapToGrid(ev.x, ev.y)
        val scoreView = context[XenakisUI].scoreView
        when {
            tool == AddTime -> {
                val amount = DecimalPrompt(
                    "How much time to add",
                    precision = 2, initialValue = 10.0, 0.0..1000.0
                ).showDialog(context) ?: return
                addTime(t, amount)
            }

            tool in setOf(Task, Memo) && ev.clickCount >= 2 -> {
                val obj = when (tool) {
                    Task -> {
                        val defaultName = context[ScoreObjectRegistry].availableName("task")
                        val name = NamePrompt(context[ScoreObjectRegistry], "Task name", defaultName)
                            .showDialog(context) ?: return
                        val code = EditorRoot.create(ScFunctionEditor(context))
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

            newObj != null -> {
                if (newObj.isNotEmpty()) {
                    createNewObject(tool, newObj, ev)
                }
                endNewObject()
            }

            ev.button == MouseButton.PRIMARY && ev.isAltDown && ev.isShiftDown -> {
                val popup = SimpleSearchableRegistryView(context[ScoreObjectRegistry], "Add object instance")
                val anchor = localToScreen(ev.x, ev.y)
                popup.showPopup(context, anchor, scene.window) { obj ->
                    val pos = snapToGrid(ev.x, ev.y)
                    val inst = ScoreObjectInstance(obj, pos)
                    score.addObject(inst)
                }
            }

            ev.button == MouseButton.PRIMARY && ev.isAltDown -> {
                val popup = SimpleSearchableRegistryView(context[SampleRegistry], "Place sample")
                val anchor = localToScreen(ev.x, ev.y)
                popup.showPopup(context, anchor, scene.window) { sample ->
                    val pos = snapToGrid(ev.x, ev.y)
                    createPlayBufObject(sample, pos)
                }
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

            this is ScoreView && !ui.playback.player.isPlaying -> {
                if (ev.isControlDown) {
                    ui.playback.attachToMainScore()
                }
                if (ui.playback.isAttachedTo(this)) {
                    ui.playback.playHead.movePlayHead(t)
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
        val obj = when (tool) {
            Synth -> {
                val def = context[InstrumentRegistry].selectedInstrument.now
                if (def !is SynthDefObject) return
                val initialName = context[ScoreObjectRegistry].availableName(def.name.now)
                val name = NamePrompt(context[ScoreObjectRegistry], "Name for new Synth object", initialName)
                    .showDialog(context) ?: return
                val ref = reactiveVariable(def.createReference())
                val controls = getDefaultControls(def)
                SynthObject(reactiveVariable(name), ref, controls)
            }

            Process -> {
                val def = context[ProcessDefRegistry].selectedDef ?: return
                val initialName = context[ScoreObjectRegistry].availableName(def.name.now)
                val name = NamePrompt(context[ScoreObjectRegistry], "Name for new Synth object", initialName)
                    .showDialog(context) ?: return
                val ref = reactiveVariable(def.createReference())
                val controls = getDefaultControls(def)
                ProcessObject(reactiveVariable(name), ref, controls)
            }

            Group -> {
                val name = context[ScoreObjectRegistry].availableName("group")
                context.compoundEdit("Add object group") {
                    val subScore = Score(mutableListOf())
                    val groupObj = ScoreObjectGroup(reactiveVariable(name), subScore)
                    val inst = rect.createInstance(groupObj)
                    addObject(inst)
                    val relativePosition = -inst.position
                    for (view in viewsInside(rect.rect.boundsInParent)) {
                        view.instance.moveInto(subScore, relativePosition, recurse = ev.isShiftDown)
                    }
                }
                return
            }

            PianoRoll -> {
                val instr = context[InstrumentRegistry].selectedInstrument.now ?: return
                compoundInput("Configure new object") {
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
                        val eventDictionary = EditorRoot.create(EventDictionaryEditor(context))
                        PianoRollObject(
                            reactiveVariable(name),
                            reactiveVariable(instr.createReference()),
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
        val inst = rect.createInstance(obj)
        addObject(inst)
    }

    private fun addObject(inst: ScoreObjectInstance) {
        score.addObject(inst)
        selector.select(getObjectView(inst), addToSelection = false)
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