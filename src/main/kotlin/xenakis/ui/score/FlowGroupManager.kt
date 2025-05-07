package xenakis.ui.score

import bundles.PublicProperty
import bundles.publicProperty
import fxutils.SubWindow
import fxutils.setupDragging
import fxutils.show
import javafx.beans.value.ObservableValue
import javafx.geometry.Point2D
import javafx.scene.control.Label
import javafx.scene.input.MouseButton
import javafx.scene.input.MouseEvent
import javafx.scene.shape.Line
import reaktive.Observer
import reaktive.value.fx.asObservableValue
import reaktive.value.now
import xenakis.impl.Decimal
import xenakis.impl.Logger
import xenakis.model.flow.AudioFlowGroup
import xenakis.model.flow.AudioFlows
import xenakis.model.registry.ObjectList
import xenakis.ui.flow.FlowGroupPane
import xenakis.ui.impl.makeSubWindow
import xenakis.ui.midi.ContextualMidiReceiver
import kotlin.math.absoluteValue

class FlowGroupManager(
    flows: AudioFlows,
    private val pane: NavigableScorePane,
) : ObjectList.Listener<AudioFlowGroup> {
    private val repaintObserver: Observer
    private val lines = mutableMapOf<AudioFlowGroup, Line>()
    private val groupPaneWindows = mutableMapOf<AudioFlowGroup, SubWindow>()
    private val tooltip = Label()

    init {
        flows.addListener(this)
        repaintObserver = pane.onRepaint.observe { _ ->
            repaint()
        }
        pane.addEventHandler(MouseEvent.MOUSE_MOVED) { ev ->
            for (line in lines.values) line.strokeWidth = 2.0
            val entry = lines.entries.find { (_, l) -> (l.startY - ev.y).absoluteValue < 3.0 }
            if (entry == null) pane.children.remove(tooltip)
            else {
                val (group, line) = entry
                line.strokeWidth = 5.0
                if (tooltip !in pane.children) pane.children.add(tooltip)
                tooltip.text = group.name.now
                tooltip.relocate(ev.x + 12.0, ev.y + 12.0)
            }
        }
    }

    private fun repaint() {
        for (line in lines.values) {
            line.endX = pane.width
            if (line !in pane.children) pane.children.add(line)
        }
    }

    override fun added(obj: AudioFlowGroup, idx: Int) {
        val line = Line()
        line.endX = pane.width
        lines[obj] = line
        setupFlowGroupLine(obj, line)
        setupDragging(line, obj)
        pane.children.add(line)
    }

    private fun setupFlowGroupLine(group: AudioFlowGroup, line: Line) {
        line.strokeProperty().bind(group.associatedColor.asObservableValue())
        val y = getLineY(group)
        line.startYProperty().bind(y)
        line.endYProperty().bind(y)
        line.setOnMouseClicked { ev ->
            if (ev.button == MouseButton.SECONDARY) {
                showGroupPane(group, Point2D(ev.x, ev.y))
            }
        }
    }

    private fun getLineY(group: AudioFlowGroup): ObservableValue<Double>? {
        val scoreY = group.yPosition.asObservableValue()
        val height = pane.heightProperty()
        val y = scoreY.flatMap { y -> height.map { h -> y.value * h.toDouble() } }
        return y
    }

    private fun setupDragging(line: Line, group: AudioFlowGroup) {
        var pos = Decimal.NaN
        line.setupDragging(
            onPressed = {
                pos = group.yPosition.now
                line.startYProperty().unbind()
                line.endYProperty().unbind()
            },
            onReleased = {
                group.yPosition.now = pos
                val y = getLineY(group)
                line.startYProperty().bind(y)
                line.endYProperty().bind(y)
            }
        ) { _, start, _, _, dy ->
            val scoreY = pane.getScoreY(start.y + dy)
            line.startY = start.y + dy
            line.endY = start.y + dy
            pos = scoreY
        }
    }

    fun showGroupPane(group: AudioFlowGroup, coords: Point2D? = null) {
        val existingWindow = groupPaneWindows[group]
        if (existingWindow != null) {
            existingWindow.showOrBringToFront()
            return
        }
        val pane = FlowGroupPane(group)
        val window = makeSubWindow(group, pane)
        group.context[ContextualMidiReceiver].registerMidiContext(window) {
            val selected = pane.flowsView.selectedObject()
            selected?.midiContext()
        }
        groupPaneWindows[group] = window
        if (coords != null) window.show(coords) else window.show()
    }

    override fun removed(obj: AudioFlowGroup) {
        val line = lines.remove(obj)
        if (line == null) {
            Logger.warn("No line found for flow group $obj", Logger.Category.Score)
            return
        }
        pane.children.remove(line)
        groupPaneWindows[obj]?.close()
        groupPaneWindows.remove(obj)
    }

    companion object: PublicProperty<FlowGroupManager> by publicProperty("FlowGroupLines")
}