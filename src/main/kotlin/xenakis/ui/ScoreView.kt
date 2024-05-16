package xenakis.ui

import hextant.fx.registerShortcuts
import hextant.serial.EditorRoot
import javafx.application.Platform
import javafx.scene.input.MouseEvent
import javafx.scene.layout.Pane
import javafx.scene.paint.Color
import javafx.scene.shape.Line
import javafx.scene.shape.Rectangle
import javafx.scene.text.Text
import xenakis.impl.Point
import xenakis.impl.step
import xenakis.model.*
import xenakis.sc.Bus
import xenakis.sc.Group
import xenakis.sc.Identifier
import xenakis.sc.Warp
import xenakis.sc.editor.ScFunctionEditor
import xenakis.ui.ToolSelector.Tool.*
import kotlin.math.exp

class ScoreView(
    private val project: XenakisProject,
    private val ui: XenakisUI
) : Pane(), ScoreListener {
    private var newObjectArea: Rectangle? = null
    private val views = mutableMapOf<ScoreObject, ScoreObjectView>()
    private val selectedViews = mutableSetOf<ScoreObjectView>()
    private var dragFrom: Point? = null
    var timeSnap: Double = 0.1
    private var displayStart: Double = 0.0
    private var displayEnd: Double = 0.0

    val pixelsPerSecond get() = width / (displayEnd - displayStart)

    val score get() = project.score

    val selectedObjects get() = selectedViews.map { view -> view.obj }

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
    }

    private fun setupDropArea() {
        setupFileDropArea(exactlyOne = true, "wav") { file, ev ->
            val defaultName = Identifier.truncate(file.nameWithoutExtension)
            val obj = SoundFileObject(
                defaultName, file,
                outBus = Bus.output, startPos = 0.0, rate = 1.0,
                envelope = xenakis.sc.Envelope.constant(1.0, Warp.Linear),
                start = getTime(ev.x), y = ev.y,
                duration = SoundFileObject.getDuration(file),
                height = 150.0, muted = false
            )
            obj.context = project.context
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
        registerShortcuts {
            on("Ctrl?+PLUS") {
                zoom(0.8, 0.0)
            }
            on("Ctrl?+MINUS") {
                zoom(1.2, 0.0)
            }
            on("HOME") {
                displayWholeScore()
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

    fun getObjectView(obj: ScoreObject) = views.getOrPut(obj) {
        when (obj) {
            is SynthObject -> SynthObjectView(obj, project)
            is TaskObject -> TaskObjectView(obj, project)
            is EnvelopeObject -> EnvelopeObjectView(obj, project)
            is SoundFileObject -> SoundFileObjectView(obj, project)
        }
    }

    override fun addedObject(obj: ScoreObject) {
        obj.initialize(project)
        val view = getObjectView(obj)
        view.addEventHandler(MouseEvent.MOUSE_CLICKED) { ev ->
            view.toFront()
            select(view, addToSelection = ev.isControlDown)
            ev.consume()
        }
        children.add(view)
        select(view, addToSelection = false)
        Platform.runLater {
            view.init(this)
        }
    }

    override fun removedObject(obj: ScoreObject) {
        val view = views.remove(obj) ?: return
        view.onRemove()
        children.remove(view)
    }

    override fun movedObject(obj: ScoreObject) {
        val view = getObjectView(obj)
        view.relocate(getX(obj.start), obj.y)
    }

    fun getX(time: Double) = (time - displayStart) * pixelsPerSecond

    fun getTime(x: Double) = (x / pixelsPerSecond) + displayStart

    fun getDuration(width: Double) = width / pixelsPerSecond

    fun getWidth(duration: Double) = duration * pixelsPerSecond

    fun select(view: ScoreObjectView, addToSelection: Boolean): Boolean {
        if (!addToSelection) deselectAll()
        if (view !in selectedViews) selectedViews.add(view)
        else selectedViews.remove(view)
        val selected = view in selectedViews
        view.setSelected(selected)
        return selected
    }

    private fun deselectAll() {
        for (v in selectedViews) v.setSelected(false)
        selectedViews.clear()
    }

    private fun mousePressed(ev: MouseEvent) {
        if (newObjectArea != null) return
        val x = ev.x.snap(timeSnap)
        val y = ev.y
        val rect = Rectangle(x, y, 0.0, 0.0)
        when (ui.toolSelector.selected.value!!) {
            Synth -> {
                val synthDef = project.context[SynthDefs].selectedSynthDef
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

            Pointer -> {
                dragFrom = Point(ev.screenX, ev.screenY)
            }

            Pattern -> TODO()
        }
        ev.consume()
    }

    private fun mouseDragged(ev: MouseEvent) {
        val s = newObjectArea
        when {
            s is Rectangle -> {
                val x = ev.x.snap(timeSnap)
                s.width = x - s.x
                s.height = ev.y - s.y
                ev.consume()
            }

            ui.toolSelector.selected.value == Pointer -> {
                val (sx, _) = dragFrom ?: return
                val dx = ev.screenX - sx
                val dt = getDuration(dx)
                displayStart -= dt
                displayEnd -= dt
                noNegativeTimes()
                repaint()
                dragFrom = Point(ev.screenX, ev.screenY)
            }
        }
    }

    private fun mouseClicked(ev: MouseEvent) {
        deselectAll()
        ev.consume()
    }

    private fun mouseReleased(ev: MouseEvent) {
        ev.consume()
        val s = newObjectArea
        val tool = ui.toolSelector.selected.value
        if (s is Rectangle && tool != Pointer) {
            if (s.width != 0.0 && s.height != 0.0) createNewObject(tool, s)
        }
        clearNewShape()
    }

    private fun createNewObject(tool: ToolSelector.Tool, rect: Rectangle) {
        val start = getTime(rect.x)
        val duration = getDuration(rect.width)
        when (tool) {
            Synth -> {
                val def = project.context[SynthDefs].selectedSynthDef
                val name = showTextInputDialog("Synth name", project.context) ?: return
                val obj = SynthObject(
                    name, Group.default, def.name.text,
                    start, duration,
                    rect.y, rect.height,
                    def.defaultControls()
                )
                obj.context = project.context
                val confirmed = ControlAssignmentView.show(obj, project)
                if (confirmed) {
                    score.addObject(obj)
                }
            }

            Task -> {
                val name = showTextInputDialog("Task name", project.context) ?: return
                val editor = EditorRoot.create(ScFunctionEditor(project.context))
                val obj = TaskObject(name, editor, start, rect.width, rect.y, rect.height, emptyList())
                obj.context = project.context
                score.addObject(obj)
            }

            Envelope -> {
                EnvelopeObjectView.showEnvelopeConfig(project.context, rect) { name, spec, outputBus ->
                    val envelope = xenakis.sc.Envelope.constant(spec.defaultValue.value, spec.warp)
                    val obj = EnvelopeObject(name, spec, outputBus, envelope, start, duration, rect.y, rect.height)
                    obj.context = project.context
                    score.addObject(obj)
                }
            }

            Pattern -> {}
            else -> {
                System.err.println("Unrecognized tool $tool")
            }
        }
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
        for (view in selectedViews) {
            score.removeObject(view.obj)
        }
    }

    override fun recolor(obj: SynthObject) {
        getObjectView(obj).recolor()
    }

    companion object {
        private val QUANTIZED_PIXELS_PER_SECOND = listOf(1.0, 2.0, 5.0, 10.0, 20.0, 50.0, 100.0, 200.0, 500.0)
    }
}