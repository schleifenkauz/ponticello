package xenakis.ui

import hextant.context.Context
import hextant.serial.EditorRoot
import javafx.application.Platform
import javafx.geometry.Bounds
import javafx.scene.input.Clipboard
import javafx.scene.input.MouseButton
import javafx.scene.input.MouseEvent
import javafx.scene.layout.Pane
import javafx.scene.paint.Color.*
import javafx.scene.shape.Rectangle
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import xenakis.impl.Arrow
import xenakis.model.*
import xenakis.model.Envelope
import xenakis.sc.Identifier
import xenakis.sc.Warp
import xenakis.sc.editor.BusSelector
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

    val selector: ObjectSelector get() = context[ObjectSelector]

    protected abstract val displayStart: Double
    protected abstract val displayEnd: Double
    abstract val timeSnap: Double

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
                is ScoreObjectGroupView -> target.scorePane
                else -> return@addEventHandler
            }
            val e = ev.copyFor(pane, pane)
            when (ev.eventType) {
                MouseEvent.MOUSE_PRESSED -> pane.mousePressed(e)
                MouseEvent.MOUSE_CLICKED -> pane.mouseClicked(e)
                MouseEvent.MOUSE_DRAGGED -> pane.mouseDragged(e)
                MouseEvent.MOUSE_RELEASED -> pane.mouseReleased(e)
            }
        }
        setupDropArea()
    }

    private fun setupDropArea() {
        setupFileDropArea(exactlyOne = true, "wav") { file, ev ->
            val defaultName = Identifier.truncate(file.nameWithoutExtension)
            val duration = SoundFileObject.getDuration(file)
            val obj = SoundFileObject(
                defaultName, file,
                outBus = BusSelector(context), startPos = 0.0, rate = 1.0,
                envelope = Envelope.constant(1.0, duration, Warp.Linear),
            )
            obj.position.set(getTime(ev.x), ev.y)
            obj.height = 150.0
            score.addObject(obj)
        }
    }

    /*
    * Score object view management
    * +*/

    fun getObjectView(obj: ScoreObject) = views.getOrPut(obj) { createObjectView(obj) }

    private fun createObjectView(obj: ScoreObject): ScoreObjectView = when (obj) {
        is SynthObject -> SynthObjectView(obj)
        is TaskObject -> TaskObjectView(obj)
        is EnvelopeObject -> EnvelopeObjectView(obj)
        is SoundFileObject -> SoundFileObjectView(obj)
        is MemoObject -> MemoObjectView(obj)
        is ScoreObjectGroup -> ScoreObjectGroupView(obj)
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
        val view = getObjectView(obj)
        view.addEventHandler(MouseEvent.MOUSE_CLICKED) { ev ->
            view.toFront()
            selector.select(view, addToSelection = ev.isAltDown)
            ev.consume()
        }
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
        val x = ev.x.snap(timeSnap)
        val y = ev.y
        val rect = Rectangle(x, y, 0.0, 0.0)
        when (selectedTool) {
            Synth -> {
                val synthDef = context[SynthDefs].selectedSynthDef
                rect.stroke = synthDef.associatedColor
            }

            Task -> rect.fill = GRAY
            Tool.Envelope -> rect.fill = WHITE
            Memo -> rect.fill = BLACK

            Compound -> {
                rect.stroke = WHITE
                rect.fill = rgb(0, 0, 0, 0.3)
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
        val x = ev.x.snap(timeSnap)
        val y = ev.y
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

    private fun mouseClicked(ev: MouseEvent) {
        if (ev.button == MouseButton.SECONDARY) {
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
        } else {
            ui.player.setPlayHeadX(ev.x)
        }
        ev.consume()
    }

    private fun mouseReleased(ev: MouseEvent) {
        ev.consume()
        if (!ev.isAltDown) selector.deselectAll()
        val newObj = newObjectArea
        val tool = ui.toolSelector.selected.value
        if (tool == AddTime) {
            showNumberPrompt("How much time to add", 0.0..1000.0, 10.0, context) { amount ->
                addTime(getTime(ev.x), amount)
            }
        } else if (newObj != null && tool != Pointer) {
            if (newObj.width != 0.0 && newObj.height != 0.0) createNewObject(tool, newObj)
            clearNewShape()
        } else if (selectedArea in children) {
            if (selectedArea.width == 0.0 || selectedArea.height == 0.0) {
                children.remove(selectedArea)
            } else if (!selectedArea.heightProperty().isBound) {
                for (view in viewsInside(selectedArea.boundsInParent)) {
                    selector.select(view, addToSelection = true)
                }
                children.remove(selectedArea)
            } else {
                selectedArea.requestFocus()
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
                val def = context[SynthDefs].selectedSynthDef
                showTextPrompt("Synth name", "", context) { name ->
                    if (!Identifier.isValid(name) || context[NamingManager].isNameTaken(name)) {
                        return@showTextPrompt false
                    }
                    val obj = SynthObject(name, def.name.text)
                    obj.controls = def.defaultControls()
                    addObject(obj, rect)
                    true
                }
            }

            Task -> {
                val editor = EditorRoot.create(ScFunctionEditor(context))
                val name = context[NamingManager].availableName(prefix = "task")
                addObject(TaskObject(name, editor, rect.width), rect)
            }

            Tool.Envelope -> {
                EnvelopeObjectView.showEnvelopeConfig(context) { name, spec, outputBus ->
                    val value = spec.defaultValue.value
                    val duration = getDuration(rect.width)
                    val envelope = Envelope.constant(value, duration, spec.warp)
                    val obj = EnvelopeObject(name, spec, outputBus, envelope)
                    addObject(obj, rect)
                }
                return
            }

            Memo -> {
                val name = context[NamingManager].availableName("memo")
                addObject(MemoObject(name, "", rect.width), rect)
            }

            Compound -> {
                val name = context[NamingManager].availableName(prefix = "sub_score")
                val objects = viewsInside(rect.boundsInParent).map { it.myObject }
                for (obj in objects) {
                    score.removeObject(obj)
                    obj.position.start -= getTime(rect.x)
                    obj.position.y -= rect.y
                }
                val subScore = Score(objects.toMutableList())
                addObject(ScoreObjectGroup(name, subScore), rect)
            }

            else -> {
                System.err.println("Unrecognized tool $tool")
                return
            }
        }

    }

    private fun addObject(obj: ScoreObject, rect: Rectangle) {
        obj.assignBoundsFromRect(rect)
        score.addObject(obj)
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