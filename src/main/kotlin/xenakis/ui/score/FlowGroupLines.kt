package xenakis.ui.score

import fxutils.SubWindow
import fxutils.setupDragging
import javafx.beans.value.ObservableValue
import javafx.scene.control.Label
import javafx.scene.input.MouseEvent
import javafx.scene.shape.Line
import reaktive.Observer
import reaktive.value.binding.map
import reaktive.value.fx.asObservableValue
import reaktive.value.now
import xenakis.impl.Decimal
import xenakis.impl.Logger
import xenakis.model.flow.AudioFlowGroup
import xenakis.model.flow.AudioFlows
import xenakis.model.registry.ObjectList
import xenakis.ui.flow.FlowGroupPane
import xenakis.ui.impl.makeSubWindow
import kotlin.math.absoluteValue

class FlowGroupLines(
    flows: AudioFlows,
    private val pane: NavigableScorePane,
) : ObjectList.Listener<AudioFlowGroup> {
    private val repaintObserver: Observer
    private val lines = mutableMapOf<AudioFlowGroup, Line>()
    private val groupPanes = mutableMapOf<AudioFlowGroup, FlowGroupPane>()
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
            if (ev.clickCount == 2) {
                showGroupPane(group)
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

    private fun showGroupPane(group: AudioFlowGroup) {
        val existingWindow = groupPaneWindows[group]
        if (existingWindow != null && existingWindow.isShowing) {
            existingWindow.toFront()
            return
        }
        val pane = groupPanes.getOrPut(group) { FlowGroupPane(group) }
        val title = group.name.map { name -> "Flow group $name" }
        val window = makeSubWindow(pane, title, group.context, SubWindow.Type.ToolWindow)
        groupPaneWindows[group] = window
        window.show()
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
        groupPanes.remove(obj)
    }
}