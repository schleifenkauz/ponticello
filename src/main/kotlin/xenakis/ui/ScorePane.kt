package xenakis.ui

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
import javafx.scene.input.*
import javafx.scene.layout.Pane
import javafx.scene.paint.Color.*
import javafx.scene.shape.Rectangle
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import reaktive.value.now
import reaktive.value.reactiveVariable
import xenakis.impl.MidiPitch
import xenakis.impl.Point
import xenakis.model.*
import xenakis.model.Envelope
import xenakis.model.Score.Companion.rootScore
import xenakis.sc.BufferControlSpec
import xenakis.sc.Identifier
import xenakis.sc.Rate
import xenakis.sc.editor.BusSelector
import xenakis.sc.editor.EventDictionaryEditor
import xenakis.sc.editor.ScFunctionEditor
import xenakis.ui.ScoreView.ClipboardMode
import xenakis.ui.ToolSelector.Tool
import xenakis.ui.ToolSelector.Tool.*
import xenakis.ui.XenakisController.Companion.currentProject
import xenakis.ui.prompt.DoublePrompt
import xenakis.ui.prompt.NamePrompt
import xenakis.ui.prompt.compoundInput

abstract class ScorePane(val score: Score, val context: Context) : Pane(), ScoreListener {
    private var newObjectArea: Rectangle? = null
    protected val selectedArea: Rectangle = Rectangle() styleClass "time-range-rect"

    private val views = mutableMapOf<ScoreObjectInstance, ScoreObjectView>()
    val allViews: Collection<ScoreObjectView> get() = views.values

    protected val ui get() = context[XenakisUI]
    private val selectedTool get() = ui.toolSelector.selected.value!!

    val selector: ScoreObjectSelectionManager get() = context[ScoreObjectSelectionManager]

    protected abstract val displayStart: Double
    protected abstract val displayEnd: Double
    abstract val rootPane: ScoreView

    abstract val xAccuracy: Int
    abstract fun snapToGrid(x: Double, y: Double): Point
    abstract fun getGrids(x: Double): List<TempoGridObjectView>
    open fun markX(x: Double) {
        getGrids(x).forEach { g -> g.mark(x) }
    }

    abstract fun getNearestGrid(x: Double, y: Double): TempoGridObjectView?

    abstract val pixelsPerSecond: Double
    fun getX(time: Double) = (time - displayStart) * pixelsPerSecond
    fun getTime(x: Double) = (x / pixelsPerSecond) + displayStart
    fun getDuration(width: Double) = width / pixelsPerSecond
    fun getWidth(duration: Double) = duration * pixelsPerSecond

    fun getScoreY(paneY: Double) = paneY / rootPane.height
    fun getPaneY(scoreY: Double) = scoreY * rootPane.height

    init {
        styleClass("score-pane")
    }

    protected open fun addTime(location: Double, amount: Double) {
        score.addTime(location, amount)
    }

    protected open fun deleteTimeRange(start: Double, end: Double) {
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
            view.setPrefSize(view.getDisplayWidth(), view.getDisplayHeight())
            view.relocate(getX(inst.start), getPaneY(inst.y))
            view.rescale()
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
            createPlayBufObject(sample, ev)
        })
    }

    private fun extractSampleFromDragBoard(db: Dragboard): SampleObject? = when {
        db.hasFiles() -> {
            val file = db.files[0]
            context[SampleRegistry].getSample(file) ?: run {
                val name = Identifier.truncate(file.nameWithoutExtension)
                val sample = SampleObject.create(context[currentProject], reactiveVariable(name), file)
                context[SampleRegistry].add(sample)
                sample
            }
        }

        db.hasContent(SampleObject.DATA_FORMAT) -> {
            context[SampleRegistry].get(db.getContent(SampleObject.DATA_FORMAT) as String)
        }

        else -> null
    }

    private fun createPlayBufObject(sample: SampleObject, ev: DragEvent) {
        val instruments = context[InstrumentRegistry]
        val synthDef = instruments.selectedInstrument.now ?: instruments.getOrNull("playbuf") ?: run {
            val playbuf = context[GlobalSynthDefLib].get("playbuf") ?: return
            instruments.add(playbuf)
            playbuf
        }
        if (synthDef !is SynthDefObject) {
            alertError("A SynthDef should be selected for sample playback to work")
            return
        }
        val bufParameter = synthDef.parameters.now.find { p -> p.name.now == "buf" }
        if (bufParameter == null) {
            alertError("No parameter 'buf' found in in SynthDef ${synthDef.name.now}")
            return
        }
        val spec = bufParameter.spec.now
        if (spec !is BufferControlSpec) {
            alertError("Parameter 'buf' of SynthDef ${synthDef.name.now} is not of type 'buf'")
            return
        }
        if (!spec.isPlayBufSource) {
            alertError("Parameter 'buf' of SynthDef ${synthDef.name.now} is not marked as a PlayBuf source")
            return
        }
        val controls = synthDef.defaultControls(context)
        val bufCtrl = controls["buf"] as BufferControl
        bufCtrl.sample.set(sample.createReference())
        val synthDefRef = reactiveVariable(synthDef.createReference())
        val name = context[ScoreObjectRegistry].availableName(sample.name.now)
        val obj = SynthObject(reactiveVariable(name), synthDefRef, controls)
        obj.setInitialSize(sample.duration, 0.05)
        val (x, y) = snapToGrid(ev.x, ev.y)
        val inst = ScoreObjectInstance(obj.createReference(), getTime(x), getScoreY(y))
        score.addObject(inst)
    }

    /*
    * Score object view management
    * +*/

    fun getObjectView(inst: ScoreObjectInstance) = views[inst] ?: error("No view found for ${inst.obj.name.now}")

    private fun createObjectView(inst: ScoreObjectInstance): ScoreObjectView = when (val obj = inst.obj) {
        is SynthObject -> SynthObjectView(inst, obj)
        is TaskObject -> TaskObjectView(inst, obj)
        is EnvelopeObject -> EnvelopeObjectView(inst, obj)
        is MemoObject -> MemoObjectView(inst, obj)
        is ScoreObjectGroup -> ScoreObjectGroupView(inst, obj)
        is PianoRollObject -> PianoRollObjectView(inst, obj)
        is TempoGridObject -> TempoGridObjectView(inst, obj)
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
        if (newObjectArea != null) return
        children.remove(selectedArea)
        val (x, y) = snapToGrid(ev.x, ev.y)
        val rect = Rectangle(x, y, 0.0, 0.0)
        when (selectedTool) {
            Synth -> {
                val synthDef = context[InstrumentRegistry].selectedInstrument.now
                if (synthDef !is SynthDefObject) return
                rect.fill = synthDef.color.now
            }

            Task, Memo -> return
            Tool.Envelope -> rect.fill = WHITE

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
                selectedArea.x = x
                selectedArea.width = 0.0
                if (ev.isShiftDown) {
                    selectedArea.y = 0.0
                    if (!selectedArea.heightProperty().isBound)
                        selectedArea.heightProperty().bind(this.heightProperty())
                } else {
                    selectedArea.heightProperty().unbind()
                    selectedArea.y = y
                    selectedArea.height = 0.0
                }
                children.add(selectedArea)
                return
            }

            Cut, AddTime -> return
        }
        setNewShape(rect)
    }

    private fun mouseDragged(ev: MouseEvent) {
        val newObj = newObjectArea
        val (x, y) = snapToGrid(ev.x, ev.y)
        when {
            newObj != null -> {
                newObj.width = x - newObj.x
                newObj.height = ev.y - newObj.y
                markX(x)
                ev.consume()
            }

            selectedArea in children && (this is ScoreView && selectedTool == Pointer || this is SubScorePane && selectedTool == Group) -> {
                if (x > selectedArea.x) {
                    selectedArea.width = x - selectedArea.x
                } else {
                    selectedArea.width += selectedArea.x - x
                    selectedArea.x = x
                }
                if (!selectedArea.heightProperty().isBound) {
                    if (y > selectedArea.y) {
                        selectedArea.height = y - selectedArea.y
                    } else {
                        selectedArea.height += selectedArea.y - y
                        selectedArea.y = y
                    }
                }
                markX(x)
            }
        }
    }

    private fun pasteFromSystemClipboard(ev: MouseEvent) {
        context.compoundEdit("Paste objects") {
            val clipboard = Clipboard.getSystemClipboard()
            if (clipboard.hasContent(ScoreObject.DATA_FORMAT)) {
                val content = clipboard.getContent(ScoreObject.DATA_FORMAT) as String
                val objects = Json.decodeFromString(serializer<List<ScoreObjectInstance>>(), content)
                val leftTop = objects.minOf { it.position }
                if (!ev.isShiftDown) selector.deselectAll()
                val (x, y) = snapToGrid(ev.x, ev.y)
                for (obj in objects) {
                    obj.moveTo(getTime(x) - leftTop.time, y - leftTop.y, simpleMove = true)
                    score.addObject(obj)
                    selector.select(getObjectView(obj), addToSelection = true)
                }
            }
        }
    }

    private fun mouseReleased(ev: MouseEvent) {
        if (ev.target != this) return
        ev.consume()
        if (score == context[rootScore] && !ev.isShiftDown) selector.deselectAll()
        val newObj = newObjectArea
        val tool = ui.toolSelector.selected.value
        when {
            tool == AddTime -> {
                val amount = DoublePrompt("How much time to add", 10.0, 0.0..1000.0).showDialog(context) ?: return
                addTime(getTime(ev.x), amount)
            }

            tool == Pointer -> {
                val scoreView = context[XenakisUI].scoreView
                if (ev.button == MouseButton.PRIMARY && scoreView.isInDuplicateMode()) {
                    var obj = scoreView.clipboardObject!!
                    if (scoreView.clipboardMode == ClipboardMode.Clone) {
                        val name = context[ScoreObjectRegistry].nameForClone(obj)
                        obj = obj.clone(name)
                    }
                    val (x, y) = snapToGrid(ev.x, ev.y)
                    val duplicate = ScoreObjectInstance(obj.createReference(), getTime(x), getScoreY(y))
                    score.addObject(duplicate)
                } else if (selectedArea in children && selectedArea.width != 0.0 && selectedArea.height != 0.0) {
                    if (!selectedArea.heightProperty().isBound) {
                        for (view in viewsInside(selectedArea.boundsInParent)) {
                            selector.select(view, addToSelection = true)
                        }
                        children.remove(selectedArea)
                    } else {
                        selectedArea.requestFocus()
                    }
                } else if (ev.button == MouseButton.SECONDARY) pasteFromSystemClipboard(ev)
                else if (this is ScoreView && !ui.player.isPlaying) ui.playHead.setPlayHeadX(ev.x)
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
                val time = getTime(ev.x)
                val y = getScoreY(ev.y)
                val inst = ScoreObjectInstance(obj.createReference(), time, y)
                score.addObject(inst)
                if (obj is MemoObject) {
                    val view = getObjectView(inst) as MemoObjectView
                    runFXWithTimeout {
                        view.enterEdit()
                    }
                }
            }

            newObj != null -> {
                if (newObj.width != 0.0 && newObj.height != 0.0) createNewObject(tool, newObj)
                clearNewShape()
            }
        }
    }

    private fun viewsInside(bounds: Bounds) = views.values.filter { v -> bounds.contains(v.boundsInParent) }

    /*
    * Object creation
    * */

    private fun createNewObject(tool: Tool, rect: Rectangle) {
        when (tool) {
            Synth -> {
                val def = context[InstrumentRegistry].selectedInstrument.now
                if (def !is SynthDefObject) return
                val initialName = context[ScoreObjectRegistry].availableName(def.name.now)
                val name = NamePrompt(context[ScoreObjectRegistry], "Name for new Synth object", initialName)
                    .showDialog(context) ?: return
                val ref = reactiveVariable(def.createReference())
                val obj = SynthObject(reactiveVariable(name), ref, controls = def.defaultControls(context))
                addNewObject(obj, rect)
            }

            Tool.Envelope -> {
                val initialName = context[ScoreObjectRegistry].availableName("env")
                val name = NamePrompt(context[ScoreObjectRegistry], "Name for new Envelope object", initialName)
                    .showDialog(context) ?: return
                val busSelector = BusSelector(context, preferredChannels = 1, preferredRate = Rate.Control)
                val spec = EnvelopeObjectView.showEnvelopeConfig(
                    context, "Control spec for envelope $name", busSelector
                ) ?: return
                val value = spec.defaultValue.get()
                val duration = getDuration(rect.width)
                val envelope = Envelope.constant(value, duration, spec.warp)
                val busRef = reactiveVariable(busSelector.selected.now)
                val obj = EnvelopeObject(reactiveVariable(name), spec, busRef, envelope)
                addNewObject(obj, rect)
            }

            Group -> {
                val name = context[ScoreObjectRegistry].availableName("group")
                context.compoundEdit("Add object group") {
                    val objects = viewsInside(rect.boundsInParent).mapTo(mutableSetOf()) { it.instance }
                    score.removeObjects(objects)
                    for (obj in objects) {
                        obj.moveTo(obj.start - getTime(rect.x), obj.y - getScoreY(rect.y), simpleMove = true)
                    }
                    val subScore = Score(objects.toMutableList())
                    addNewObject(ScoreObjectGroup(reactiveVariable(name), subScore), rect)
                }
            }

            PianoRoll -> {
                val instr = context[InstrumentRegistry].selectedInstrument.now ?: return
                compoundInput<Unit>("Configure new object") {
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
                        val obj = PianoRollObject(
                            reactiveVariable(name),
                            reactiveVariable(instr.createReference()),
                            lowestPitch,
                            highestPitch,
                            eventDictionary,
                            notes
                        )
                        addNewObject(obj, rect)
                    }
                }.showDialog(context)
            }

            TempoGrid -> {
                val name = context[ScoreObjectRegistry].availableName("grid")
                val obj = TempoGridObject.createDefault(name)
                addNewObject(obj, rect)
            }

            Pointer, Cut, AddTime, Task, Memo -> {
                System.err.println("Unrecognized object type $tool")
                return
            }
        }
    }

    private fun addNewObject(obj: ScoreObject, rect: Rectangle) {
        obj.setInitialSize(getDuration(rect.width), getScoreY(rect.height))
        val inst = ScoreObjectInstance(obj.createReference(), getTime(rect.x), getScoreY(rect.y))
        score.addObject(inst)
        selector.select(getObjectView(inst), addToSelection = false)
    }

    /*
    * Object creation rectangle
    * */

    private fun setNewShape(s: Rectangle) {
        if (s !in children) children.add(s)
        newObjectArea = s
    }

    fun clearNewShape() {
        if (newObjectArea != null) {
            children.remove(newObjectArea!!)
            newObjectArea = null
        }
    }

    fun removeSelected() {
        if (selectedArea in children && selectedArea.heightProperty().isBound) {
            val start = getTime(selectedArea.x)
            val end = getTime(selectedArea.x + selectedArea.width)
            deleteTimeRange(start, end)
            children.remove(selectedArea)
        } else {
            selector.removeSelected()
        }
        selector.deselectAll()

    }
}