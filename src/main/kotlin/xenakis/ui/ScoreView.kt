package xenakis.ui

import hextant.serial.EditorRoot
import hextant.undo.UndoManager
import javafx.application.Platform
import javafx.scene.input.Clipboard
import javafx.scene.input.MouseButton
import javafx.scene.input.MouseEvent
import javafx.scene.layout.Pane
import javafx.scene.paint.Color
import javafx.scene.paint.Color.BLACK
import javafx.scene.shape.Line
import javafx.scene.shape.Rectangle
import javafx.scene.text.Text
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import reaktive.value.ReactiveValue
import reaktive.value.reactiveVariable
import xenakis.impl.step
import xenakis.model.*
import xenakis.sc.Identifier
import xenakis.sc.Warp
import xenakis.sc.editor.BusRefEditor
import xenakis.sc.editor.ScFunctionEditor
import xenakis.ui.ToolSelector.Tool.*
import xenakis.ui.ToolSelector.Tool.Envelope
import kotlin.math.exp

class ScoreView(
    private val project: XenakisProject,
    private val ui: XenakisUI
) : Pane(), ScoreListener {
    private var newObjectArea: Rectangle? = null
    private var selectedArea: Rectangle = Rectangle() styleClass "time-range-rect"
    private val positionTracker = Line() styleClass "mouse-tracker-line"
    private val views = mutableMapOf<ScoreObject, ScoreObjectView>()
    private val selectedViews = mutableSetOf<ScoreObjectView>()
    var timeSnap: Double = 0.1
    private var displayStart: Double = 0.0
    private var displayEnd: Double = 0.0

    val pixelsPerSecond get() = width / (displayEnd - displayStart)

    val score get() = project.score

    val context get() = project.context

    private val selectedTool get() = ui.toolSelector.selected.value!!

    val selectedObjects
        get() = selectedViews.map { view -> view.myObject }

    private val _singleSelected = reactiveVariable<ScoreObjectView?>(null)
    val singleSelected: ReactiveValue<ScoreObjectView?> get() = _singleSelected

    init {
        listenForEvents()
        score.addListener(this)
        styleClass.add("score-view")
        widthProperty().addListener { _, old, new -> onResize(old.toDouble(), new.toDouble()) }
    }

    private fun onResize(old: Double, new: Double) {
        val delta = new - old
        displayEnd += getDuration(delta)
        repaint()
    }

    fun displayWholeScore() {
        displayStart = 0.0
        displayEnd = score.objects.maxOfOrNull { obj -> obj.start + obj.duration } ?: 60.0
        repaint()
    }

    private fun repaint() {
        children.clear()
        displayTimeGrid()
        displayObjects()
        ui.player.repaint()
    }

    private fun displayTimeGrid() {
        var idx = QUANTIZED_PIXELS_PER_SECOND.binarySearchBy(pixelsPerSecond) { s -> s }
        if (idx < 0) idx = (-(idx + 1)).coerceAtMost(QUANTIZED_PIXELS_PER_SECOND.size - 1)
        val quantizedPixelsPerSecond = QUANTIZED_PIXELS_PER_SECOND[idx]
        val gridDist = (1 / quantizedPixelsPerSecond) * 50.0
        timeSnap = getWidth(gridDist) / 10.0
        val accuracy = accuracy(gridDist)
        for (t in displayStart..displayEnd step gridDist) {
            val x = getX(t)
            val l = Line(x, 20.0, x, height - 40.0).styleClass("grid-line")
            l.endYProperty().bind(heightProperty().subtract(40))
            children.add(l)
            val timeCode = timeCode(t, accuracy)
            val txt = Text(timeCode).styleClass("grid-time-code")
            txt.relocate(x - 8, height - 20)
            txt.layoutYProperty().bind(heightProperty().subtract(20))
            children.add(txt)
        }
    }

    private fun displayObjects() {
        for ((obj, view) in views) {
            if (obj.start > displayEnd) continue
            if (obj.start + obj.duration < displayStart) continue
            view.prefWidth = view.getDisplayWidth()
            view.relocate(getX(obj.start), obj.y)
            children.add(view)
        }
    }

    private fun listenForEvents() {
        setOnMousePressed(this::mousePressed)
        setOnMouseClicked(this::mouseClicked)
        setOnMouseDragged(this::mouseDragged)
        setOnMouseReleased(this::mouseReleased)
        setupNavigation()
        setupDropArea()
        setupPositionTracker()
    }

    private fun setupPositionTracker() {
        positionTracker.startY = 10.0
        positionTracker.endYProperty().bind(heightProperty().subtract(10))
        positionTracker.viewOrder = 1.0
        setOnMouseEntered { ev ->
            positionTracker.layoutX = ev.x.snap(timeSnap)
            children.add(positionTracker)
            ev.consume()
        }
        setOnMouseMoved { ev ->
            positionTracker.layoutX = ev.x.snap(timeSnap)
            ev.consume()
        }
        setOnMouseExited { ev ->
            children.remove(positionTracker)
            ev.consume()
        }
    }

    private fun setupDropArea() {
        setupFileDropArea(exactlyOne = true, "wav") { file, ev ->
            val defaultName = Identifier.truncate(file.nameWithoutExtension)
            val obj = SoundFileObject(
                defaultName, file,
                outBus = BusRefEditor(context), startPos = 0.0, rate = 1.0,
                envelope = xenakis.model.Envelope.constant(1.0, Warp.Linear),
            )
            obj.position.set(getTime(ev.x), ev.y)
            obj.height = 150.0
            score.addObject(obj)
        }
    }

    private fun setupNavigation() {
        setOnScroll { ev ->
            if (ev.isControlDown) {
                val factor = exp(-ev.deltaY * 0.002)
                zoom(factor, ev.x)
            } else {
                scroll(-ev.deltaX / pixelsPerSecond)
            }
        }
    }

    private fun zoom(amount: Double, evX: Double) {
        val newIntervalSize = (displayEnd - displayStart) * amount
        val oldIntervalCenter = (displayEnd + displayStart) / 2
        val newIntervalCenter = (getTime(evX) + oldIntervalCenter * 3) / 4
        displayStart = newIntervalCenter - (newIntervalSize / 2)
        displayEnd = newIntervalCenter + (newIntervalSize / 2)
        noNegativeTimes()
        repaint()
    }

    private fun scroll(amount: Double) {
        displayStart += amount
        displayEnd += amount
        noNegativeTimes()
        repaint()
    }

    private fun noNegativeTimes() {
        if (displayStart < 0) {
            displayEnd -= displayStart
            displayStart -= displayStart
        }
    }

    fun getObjectView(obj: ScoreObject) = views.getOrPut(obj) { createObjectView(obj) }

    private fun createObjectView(obj: ScoreObject): ScoreObjectView = when (obj) {
        is SynthObject -> SynthObjectView(obj)
        is TaskObject -> TaskObjectView(obj)
        is EnvelopeObject -> EnvelopeObjectView(obj)
        is SoundFileObject -> SoundFileObjectView(obj)
        is MemoObject -> MemoObjectView(obj)
        is ClonedObject -> {
            val view = createObjectView(obj.original)
            view.myObject = obj
            view
        }

        else -> throw AssertionError()
    }

    override fun addedObject(obj: ScoreObject) {
        val view = getObjectView(obj)
        view.addEventHandler(MouseEvent.MOUSE_CLICKED) { ev ->
            view.toFront()
            select(view, addToSelection = ev.isAltDown)
            ev.consume()
        }
        children.add(view)
        Platform.runLater {
            view.init(this)
            view.requestFocus()
        }
    }

    override fun removedObject(obj: ScoreObject) {
        val view = views.remove(obj) ?: return
        children.remove(view)
    }

    fun getX(time: Double) = (time - displayStart) * pixelsPerSecond

    fun getTime(x: Double) = (x / pixelsPerSecond) + displayStart

    fun getDuration(width: Double) = width / pixelsPerSecond

    fun getWidth(duration: Double) = duration * pixelsPerSecond

    fun select(view: ScoreObjectView, addToSelection: Boolean): Boolean {
        if (!addToSelection) deselectAll()
        if (view !in selectedViews) selectedViews.add(view)
        else selectedViews.remove(view)
        _singleSelected.set(selectedViews.singleOrNull())
        val selected = view in selectedViews
        view.setSelected(selected)
        return selected
    }

    fun deselectAll() {
        for (v in selectedViews) v.setSelected(false)
        selectedViews.clear()
    }

    private fun mousePressed(ev: MouseEvent) {
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
                rect.fill = Color.TRANSPARENT
                setNewShape(rect)
            }

            Task -> {
                rect.fill = Color.GRAY
                setNewShape(rect)
            }

            Envelope -> {
                rect.fill = Color.WHITE
                setNewShape(rect)
            }

            Memo -> {
                rect.fill = BLACK
                setNewShape(rect)
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
            }

            AddTime -> {}
        }
        ev.consume()
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
                    obj.position.start += getTime(ev.x) - leftTop.start
                    obj.position.start = obj.start.coerceAtLeast(0.0)
                    obj.position.y += ev.y - leftTop.y
                    obj.position.y = obj.y.coerceIn(0.0, height - obj.height)
                    score.addObject(obj)
                }
            }
        } else {
            ui.player.setPlayHeadX(ev.x)
        }
        ev.consume()
    }

    private fun mouseReleased(ev: MouseEvent) {
        ev.consume()
        if (!ev.isAltDown) deselectAll()
        val newObj = newObjectArea
        val tool = ui.toolSelector.selected.value
        if (tool == AddTime) {
            project.addTime(getTime(ev.x))
        } else if (newObj != null && tool != Pointer) {
            if (newObj.width != 0.0 && newObj.height != 0.0) createNewObject(tool, newObj)
            clearNewShape()
        } else if (selectedArea in children) {
            if (selectedArea.width == 0.0 || selectedArea.height == 0.0) {
                children.remove(selectedArea)
            } else if (!selectedArea.heightProperty().isBound) {
                for ((_, view) in views) {
                    if (selectedArea.boundsInParent.contains(view.boundsInParent)) {
                        select(view, addToSelection = true)
                    }
                }
                children.remove(selectedArea)
            } else {
                selectedArea.requestFocus()
            }
        }
    }

    private fun createNewObject(tool: ToolSelector.Tool, rect: Rectangle) {
        val obj = when (tool) {
            Synth -> {
                val def = context[SynthDefs].selectedSynthDef
                val name = showTextInputDialog("Synth name", context) ?: return
                val obj = SynthObject(name, def.name.text)
                obj.controls = def.defaultControls()
                obj.context = context
                obj
            }

            Task -> {
                val editor = EditorRoot.create(ScFunctionEditor(context))
                TaskObject("task", editor, rect.width)
            }

            Envelope -> {
                EnvelopeObjectView.showEnvelopeConfig(context) { name, spec, outputBus ->
                    val envelope = xenakis.model.Envelope.constant(spec.defaultValue.value, spec.warp)
                    val obj = EnvelopeObject(name, spec, outputBus, envelope)
                    obj.assignBoundsFromRect(rect)
                    score.addObject(obj)
                }
                return
            }

            Memo -> MemoObject("memo", "", rect.width)

            else -> {
                System.err.println("Unrecognized tool $tool")
                return
            }
        }
        obj.assignBoundsFromRect(rect)
        score.addObject(obj)
    }

    private fun ScoreObject.assignBoundsFromRect(r: Rectangle) {
        position.set(getTime(r.x), r.y)
        duration = getDuration(r.width)
        height = r.height
    }

    private fun setNewShape(s: Rectangle) {
        children.add(s)
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
            score.deleteTimeRange(start, end)
            children.remove(selectedArea)
        } else {
            context[UndoManager].beginCompoundEdit()
            for (view in selectedViews) {
                score.removeObject(view.myObject)
            }
            context[UndoManager].finishCompoundEdit("Remove objects")
        }
        deselectAll()
        _singleSelected.set(null)
    }

    fun copySelected() {
        if (selectedObjects.isEmpty()) return
        val json = Json.encodeToString(ListSerializer(ScoreObject.Ser), selectedObjects)
        val clipboard = Clipboard.getSystemClipboard()
        clipboard.setContent(mapOf(ScoreObject.DATA_FORMAT to json))
    }

    fun toggleMuteSelected() {
        context[UndoManager].beginCompoundEdit("Toggle mute")
        for (obj in selectedObjects) {
            obj.muted = !obj.muted
        }
        context[UndoManager].finishCompoundEdit()
    }

    companion object {
        private val QUANTIZED_PIXELS_PER_SECOND = listOf(1.0, 2.0, 5.0, 10.0, 20.0, 50.0, 100.0, 200.0, 500.0)
    }
}