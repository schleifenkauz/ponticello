package xenakis.ui

import hextant.context.Context
import hextant.serial.EditorRoot
import javafx.application.Platform
import javafx.collections.FXCollections
import javafx.geometry.Bounds
import javafx.geometry.Pos
import javafx.scene.control.ComboBox
import javafx.scene.control.Label
import javafx.scene.control.Spinner
import javafx.scene.control.TextField
import javafx.scene.input.Clipboard
import javafx.scene.input.MouseButton
import javafx.scene.input.MouseEvent
import javafx.scene.layout.HBox
import javafx.scene.layout.Pane
import javafx.scene.layout.VBox
import javafx.scene.paint.Color.*
import javafx.scene.shape.Rectangle
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import reaktive.value.now
import xenakis.impl.Arrow
import xenakis.impl.MidiPitch
import xenakis.impl.Point
import xenakis.model.*
import xenakis.model.Envelope
import xenakis.sc.Identifier
import xenakis.sc.Rate
import xenakis.sc.editor.BusSelector
import xenakis.sc.editor.EventDictionaryEditor
import xenakis.sc.editor.ScFunctionEditor
import xenakis.ui.ToolSelector.Tool
import xenakis.ui.ToolSelector.Tool.*

abstract class ScorePane(val score: Score, val context: Context) : Pane(), ScoreListener {
    private var newObjectArea: Rectangle? = null
    protected val selectedArea: Rectangle = Rectangle() styleClass "time-range-rect"

    private val outgoingArrows = mutableMapOf<ScoreObjectView, Pair<Arrow, ScoreObjectView>>()
    private val ingoingArrows = mutableMapOf<ScoreObjectView, Pair<Arrow, ScoreObjectView>>()

    private val views = mutableMapOf<ScoreObject, ScoreObjectView>()
    val allViews: Collection<ScoreObjectView> get() = views.values

    protected val ui get() = context[XenakisUI]
    private val selectedTool get() = ui.toolSelector.selected.value!!

    val selector: ScoreObjectSelector get() = context[ScoreObjectSelector]

    protected abstract val displayStart: Double
    protected abstract val displayEnd: Double

    abstract val xAccuracy: Int
    abstract fun snapToGrid(x: Double, y: Double): Point

    abstract val pixelsPerSecond: Double
    fun getX(time: Double) = (time - displayStart) * pixelsPerSecond
    fun getTime(x: Double) = (x / pixelsPerSecond) + displayStart
    fun getDuration(width: Double) = width / pixelsPerSecond
    fun getWidth(duration: Double) = duration * pixelsPerSecond

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
        for ((obj, view) in views) {
            if (obj.start > displayEnd) continue
            if (obj.start + obj.duration < displayStart) continue
            view.prefWidth = view.getDisplayWidth()
            view.relocate(getX(obj.start), obj.y)
            view.rescale()
            children.add(view)
        }
        for ((_, v) in outgoingArrows) {
            val (arr, _) = v
            children.add(arr)
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
        setupDropArea({ db -> db.hasFile("wav") || db.hasContent(BufferObject.DATA_FORMAT) }, { ev ->
            val db = ev.dragboard
            val buf = if (db.hasFiles()) {
                val file = db.files[0]
                val defaultName = Identifier.truncate(file.nameWithoutExtension)
                val buffer = FileBuffer.create(file, defaultName)
                context[BufferRegistry].add(buffer)
                buffer
            } else {
                context[BufferRegistry].get(db.getContent(BufferObject.DATA_FORMAT) as String)
            }
            val obj = PlayBufObject.create(buf, context) ?: return@setupDropArea
            obj.position.set(getTime(ev.x), ev.y)
            obj.height = 150.0
            score.addObject(obj)
        })
    }

    /*
    * Score object view management
    * +*/

    fun getObjectView(obj: ScoreObject) = views[obj] ?: error("No view found for ${obj.name.now}")

    fun createObjectView(obj: ScoreObject): ScoreObjectView = when (obj) {
        is SynthObject -> SynthObjectView(obj)
        is TaskObject -> TaskObjectView(obj)
        is EnvelopeObject -> EnvelopeObjectView(obj)
        is PlayBufObject -> PlayBufObjectView(obj)
        is MemoObject -> MemoObjectView(obj)
        is ScoreObjectGroup -> ScoreObjectGroupView(obj)
        is PianoRollObject -> PianoRollObjectView(obj)
        is TempoGridObject -> TempoGridObjectView(obj)
        is ClonedObject -> {
            val view = createObjectView(obj.original)
            view.myObject = obj
            view
        }

        else -> throw AssertionError()
    }

    /*
    * Score change handlers
    * */

    override fun addedObject(obj: ScoreObject) {
        val view = createObjectView(obj)
        views[obj] = view
        children.add(view)
        view.initialize(this)
        Platform.runLater {
            view.requestFocus()
        }
    }

    override fun removedObject(obj: ScoreObject) {
        val view = views.remove(obj) ?: return
        children.remove(view)
        ingoingArrows.remove(view)?.let { (arr, _) -> children.remove(arr) }
        outgoingArrows.remove(view)?.let { (arr, _) -> children.remove(arr) }
    }

    override fun chained(previous: ScoreObject, next: ScoreObject) {
        val prevView = getObjectView(previous)
        val nextView = getObjectView(next)
        val arrow = Arrow() styleClass "chain-arrow"
        arrow.setOnMouseClicked { ev ->
            if (ev.button == MouseButton.SECONDARY) {
                score.unchain(previous)
            }
        }
        paintArrow(arrow, prevView, nextView)
        outgoingArrows[prevView] = arrow to nextView
        ingoingArrows[nextView] = arrow to prevView
        children.add(arrow)
    }

    override fun unchained(previous: ScoreObject, next: ScoreObject) {
        val prevView = getObjectView(previous)
        val (_, arrow) = outgoingArrows.remove(prevView) ?: error("$previous was not chained to $next")
        children.remove(arrow)
    }

    private fun paintArrow(arr: Arrow, prev: ScoreObjectView, nxt: ScoreObjectView) {
        arr.setStart(prev.boundsInParent.maxX, prev.boundsInParent.middleY)
        arr.setEnd(nxt.boundsInParent.minX, nxt.boundsInParent.middleY)
        prev.boundsInParentProperty().addListener { _, _, bounds ->
            arr.setStart(bounds.maxX, bounds.middleY)
        }
        nxt.boundsInParentProperty().addListener { _, _, bounds ->
            arr.setEnd(bounds.minX, bounds.middleY)
        }
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
                val synthDef = context[InstrumentRegistry].selectedInstrument
                if (synthDef !is SynthDefObject) return
                rect.fill = synthDef.color.now
            }

            Task -> rect.fill = GRAY
            Tool.Envelope -> rect.fill = WHITE
            Memo -> rect.fill = BLACK

            Group -> {
                rect.stroke = WHITE
                rect.fill = rgb(0, 0, 0, 0.3)
            }

            PianoRoll -> rect.fill = context[InstrumentRegistry].selectedInstrument?.color?.now ?: return

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

            Cut -> return
            AddTime -> return
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
                ev.consume()
            }

            selectedArea in children && selectedTool == Pointer -> {
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
            }
        }
    }

    private fun pasteFromClipboard(ev: MouseEvent) {
        val clipboard = Clipboard.getSystemClipboard()
        if (clipboard.hasContent(ScoreObject.DATA_FORMAT)) {
            val content = clipboard.getContent(ScoreObject.DATA_FORMAT) as String
            val objects = Json.decodeFromString(ListSerializer(ScoreObject.Ser), content)
            val leftTop = objects.minOf { it.position }
            for (obj in objects) {
                score.addObject(obj)
                obj.position.start += getTime(ev.x) - leftTop.start
                obj.position.start = obj.start.coerceAtLeast(0.0)
                obj.position.y += ev.y - leftTop.y
                obj.position.y = obj.y.coerceIn(0.0, height - obj.height)
            }
        }
    }

    private fun mouseReleased(ev: MouseEvent) {
        ev.consume()
        if (!ev.isShiftDown) selector.deselectAll()
        val newObj = newObjectArea
        val tool = ui.toolSelector.selected.value
        when {
            tool == AddTime -> {
                showNumberPrompt("How much time to add", 0.0..1000.0, 10.0, context) { amount ->
                    addTime(getTime(ev.x), amount)
                }
            }

            tool == Pointer -> {
                if (selectedArea in children && selectedArea.width != 0.0 && selectedArea.height != 0.0) {
                    if (!selectedArea.heightProperty().isBound) {
                        for (view in viewsInside(selectedArea.boundsInParent)) {
                            selector.select(view, addToSelection = true)
                        }
                        children.remove(selectedArea)
                    } else {
                        selectedArea.requestFocus()
                    }
                } else if (ev.button == MouseButton.SECONDARY) pasteFromClipboard(ev)
                else if (this is ScoreView) ui.player.setPlayHeadX(ev.x)
            }

            newObj != null -> {
                if (newObj.width != 0.0 && newObj.height != 0.0) createNewObject(tool, newObj)
                clearNewShape()
            }
        }
    }

    private fun viewsInside(bounds: Bounds) = views.values.filter { v -> bounds.contains(v.boundsInParent) }

    private fun promptNewObjectName(prompt: String, initialName: String, create: (String) -> Unit) {
        showTextPrompt(prompt, initialName, context) { name ->
            if (!Identifier.isValid(name) || context[ScoreObjectRegistry].has(name)) {
                return@showTextPrompt false
            }
            create(name)
            true
        }
    }

    /*
    * Object creation
    * */

    private fun createNewObject(tool: Tool, rect: Rectangle) {
        when (tool) {
            Synth -> {
                val def = context[InstrumentRegistry].selectedInstrument
                if (def !is SynthDefObject) return
                val defaultGroup = context[GroupRegistry].getDefault()
                val initialName = context[ScoreObjectRegistry].availableName("synth")
                promptNewObjectName("Synth name", initialName) { name ->
                    @Suppress("UNCHECKED_CAST")
                    val ref = def.createReference() as ObjectReference<SynthDefObject>
                    val obj = SynthObject(
                        name,
                        ref, defaultGroup.createReference(),
                        _controls = def.defaultControls(context)
                    )
                    addObject(obj, rect)
                }
            }

            Task -> {
                val name = context[ScoreObjectRegistry].availableName("task")
                val editor = EditorRoot.create(ScFunctionEditor(context))
                addObject(TaskObject(name, editor, rect.width), rect)
            }

            Tool.Envelope -> promptNewObjectName(
                "Envelope name",
                context[ScoreObjectRegistry].availableName("env")
            ) { name ->
                val busSelector = BusSelector(context, preferredChannels = 1, preferredRate = Rate.Control)
                EnvelopeObjectView.showEnvelopeConfig(context, busSelector) { spec ->
                    val value = spec.defaultValue.get()
                    val duration = getDuration(rect.width)
                    val envelope = Envelope.constant(value, duration, spec.warp)
                    val obj = EnvelopeObject(name, spec, busSelector.result.now, envelope)
                    addObject(obj, rect)
                }
            }

            Memo -> {
                val name = context[ScoreObjectRegistry].availableName("memo")
                addObject(MemoObject(name, "", rect.width), rect)
            }

            Group -> {
                val name = context[ScoreObjectRegistry].availableName("group")
                val objects = viewsInside(rect.boundsInParent).map { it.myObject }
                for (obj in objects) {
                    score.removeObject(obj)
                    obj.position.start -= getTime(rect.x)
                    obj.position.y -= rect.y
                }
                val subScore = Score(objects.toMutableList())
                addObject(ScoreObjectGroup(name, subScore), rect)
            }

            PianoRoll -> {
                val instr = context[InstrumentRegistry].selectedInstrument ?: return
                val defaultName = context[ScoreObjectRegistry].availableName("piano_roll")
                val nameField = TextField(defaultName)
                val rootPitchSelector = ComboBox(FXCollections.observableList(MidiPitch.allPitchClasses()))
                rootPitchSelector.value = MidiPitch(0)
                val registerSpinner = Spinner<Int>(0, 10, 4)
                val octaves = Spinner<Int>(1, 12, 2)
                val layout = VBox(
                    HBox(Label("Name: ").setPreferredWidth(150.0), nameField).centerChildrenVertically(),
                    HBox(
                        Label("Root pitch class: ").setPreferredWidth(150.0),
                        rootPitchSelector
                    ).centerChildrenVertically(),
                    HBox(Label("Base register: ").setPreferredWidth(150.0), registerSpinner).centerChildrenVertically(),
                    HBox(Label("Octaves: ").setPreferredWidth(150.0), octaves).centerChildrenVertically()
                )
                layout.alignment = Pos.CENTER_LEFT
                val obj = layout.showDialog("Configure PianoRoll", context) {
                    val name = nameField.text
                    if (!Identifier.isValid(name) || context[ScoreObjectRegistry].has(name)) return@showDialog null
                    val lowestPitch = rootPitchSelector.value.step + 12 * registerSpinner.value
                    val highestPitch = lowestPitch + 12 * octaves.value
                    val notes = mutableListOf<PianoRollObject.Note>()
                    val eventDictionary = EditorRoot.create(EventDictionaryEditor(context))
                    PianoRollObject(name, instr.createReference(), lowestPitch, highestPitch, eventDictionary, notes)
                } ?: return
                addObject(obj, rect)
            }

            TempoGrid -> {
                val name = context[ScoreObjectRegistry].availableName("grid")
                val obj = TempoGridObject.createDefault(name)
                val configLayout = HBox(10.0)
                TempoGridObjectView.createConfigurationBar(configLayout, obj)
                val result = configLayout.showDialog("Configure tempo grid", context) { obj }
                if (result != null) {
                    addObject(obj, rect)
                }
            }

            Pointer, Cut, AddTime -> {
                System.err.println("Unrecognized tool $tool")
                return
            }
        }
    }

    private fun addObject(obj: ScoreObject, rect: Rectangle) {
        obj.assignBoundsFromRect(rect)
        score.addObject(obj)
        selector.select(getObjectView(obj), addToSelection = false)
    }

    /*
    * Object creation rectangle
    * */

    private fun ScoreObject.assignBoundsFromRect(r: Rectangle) {
        position.set(getTime(r.x), r.y)
        duration = getDuration(r.width)
        height = r.height
    }

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