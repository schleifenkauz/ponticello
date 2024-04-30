package xenakis.ui

import javafx.application.Platform
import javafx.scene.Node
import javafx.scene.input.MouseEvent
import javafx.scene.layout.Pane
import javafx.scene.paint.Color
import javafx.scene.shape.Line
import javafx.scene.shape.Rectangle
import javafx.scene.text.Text
import xenakis.impl.step
import xenakis.model.*
import xenakis.sc.Group
import xenakis.sc.editor.ScFunctionEditor
import xenakis.ui.ToolSelector.Tool.*

class ScoreView(
    private val project: XenakisProject,
    private val ui: XenakisUI
) : Pane(), ScoreListener {
    private var newObjectArea: Rectangle? = null
    private val views = mutableMapOf<ScoreObject, ScoreObjectView>()
    private val selectedViews = mutableSetOf<ScoreObjectView>()
    private val orientationNodes = mutableListOf<Node>()
    var timeSnap: Double = 0.1

    val score get() = project.score

    init {
        addTimeGrid()
        scaleXProperty().addListener { _ -> addTimeGrid() }
        listenForMouseEvents()
        displayScore()
        styleClass.add("score-view")
    }

    private fun addTimeGrid() {
        children.removeAll(orientationNodes)
        orientationNodes.clear()
        val steps = listOf(0.1, 0.2, 0.5, 1.0, 2.0, 5.0, 10.0)
        var idx = steps.binarySearchBy(1 / scaleX) { s -> s }
        if (idx < 0) idx = (-(idx + 1)).coerceAtMost(steps.size - 1)
        val gridDist = steps[idx]
        timeSnap = PIXELS_PER_SECOND * gridDist / 10.0
        val accuracy = if (gridDist - gridDist.toInt() == 0.0) 0 else gridDist.toString().substringAfter(".").length
        for (t in gridDist..(score.totalDuration - gridDist) step gridDist) {
            val x = t * PIXELS_PER_SECOND
            val l = Line(x, 20.0, x, height - 40.0).styleClass("grid-line").antiScale(this)
            orientationNodes.add(l)
            val timeCode = timeCode(t, accuracy)
            val txt = Text(timeCode).styleClass("grid-time-code")
            txt.relocate(x - 8, height - 20)
            txt.antiScale(this)
            orientationNodes.add(txt)
        }
        children.addAll(0, orientationNodes)
    }

    private fun displayScore() {
        score.addListener(this)
        for (obj in score.objects) {
            addedObject(obj)
        }
        prefHeight = 1000.0
        setTotalDuration(score.totalDuration)
    }

    private fun listenForMouseEvents() {
        setOnMousePressed(this::mousePressed)
        setOnMouseClicked(this::mouseClicked)
        setOnMouseDragged(this::mouseDragged)
        setOnMouseReleased(this::mouseReleased)
    }

    fun getObjectView(obj: ScoreObject) = views.getOrPut(obj) {
        when (obj) {
            is SynthObject -> SynthObjectView(obj, project)
            is TaskObject -> TaskObjectView(obj, project)
            is EnvelopeObject -> TODO()
            else -> TODO()
        }
    }

    override fun addedObject(obj: ScoreObject) {
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
        val view = getObjectView(obj)
        view.onRemove()
        children.remove(view)
    }

    override fun setTotalDuration(duration: Double) {
        prefWidth = duration * PIXELS_PER_SECOND
        addTimeGrid()
    }

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
        when (ui.toolSelector.selected.value) {
            ToolSelector.Tool.Synth -> {
                rect.stroke = ui.synthDefsEditor.selectedSynthDef.associatedColor
                rect.fill = Color.TRANSPARENT
                setNewShape(rect)
            }

            ToolSelector.Tool.Task -> {
                rect.fill = Color.GRAY
                setNewShape(rect)
            }

            else -> {

            }
        }
        ev.consume()
    }

    private fun mouseDragged(ev: MouseEvent) {
        val x = ev.x.snap(timeSnap)
        when (val s = newObjectArea) {
            is Rectangle -> {
                s.width = x - s.x
                s.height = ev.y - s.y
                ev.consume()
            }

            else -> {}
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
        val start = rect.x / PIXELS_PER_SECOND
        val duration = rect.width / PIXELS_PER_SECOND
        when (tool) {
            Synth -> {
                val def = ui.synthDefsEditor.selectedSynthDef
                val name = showTextInputDialog("Synth name", project.context) ?: return
                val obj = SynthObject(
                    name, Group.default, def.name,
                    start, duration,
                    rect.y, rect.height,
                    def.defaultControls()
                )
                obj.initialize(project)
                val confirmed = ControlAssignmentView.show(obj, project)
                if (confirmed) {
                    score.addObject(obj)
                }
            }

            Task -> {
                val name = showTextInputDialog("Task name", project.context) ?: return
                val editor = ScFunctionEditor(project.context)
                val obj = TaskObject(name, editor, start, duration, rect.y, rect.height, emptyList())
                obj.initialize(project)
                score.addObject(obj)
            }

            Envelope -> {

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

    companion object {
        const val PIXELS_PER_SECOND = 50.0
    }
}