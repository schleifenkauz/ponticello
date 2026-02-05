package ponticello.ui.score

import bundles.PublicProperty
import bundles.publicProperty
import fxutils.drag.setupDragging
import fxutils.modifiers
import fxutils.setPseudoClassState
import fxutils.undo.UndoManager
import fxutils.undo.VariableEdit
import javafx.beans.value.ObservableValue
import javafx.scene.control.Label
import javafx.scene.input.MouseButton
import javafx.scene.input.MouseEvent
import javafx.scene.shape.Line
import ponticello.impl.Decimal
import ponticello.impl.Logger
import ponticello.model.flow.AudioFlowGroup
import ponticello.model.flow.AudioFlows
import ponticello.model.registry.ObjectList
import ponticello.ui.dock.AppLayout
import ponticello.ui.flow.AudioFlowsPane
import reaktive.Observer
import reaktive.value.fx.asObservableValue
import reaktive.value.now
import kotlin.math.absoluteValue

class FlowGroupManager(
    flows: AudioFlows,
    private val pane: NavigableScorePane,
) : ObjectList.Listener<AudioFlowGroup> {
    private val repaintObserver: Observer
    private val lines = mutableMapOf<AudioFlowGroup, Line>()
    private val tooltip = Label()

    private val audioFlowsPane by lazy { pane.context[AppLayout].get<AudioFlowsPane>(setup = false) }

    init {
        flows.addListener(this)
        repaintObserver = pane.onRepaint.observe { _ ->
            repaint()
        }
        pane.addEventHandler(MouseEvent.MOUSE_MOVED) { ev ->
            for ((group, line) in lines) {
                line.strokeWidth = 2.0
                val flowsPane = audioFlowsPane
                if (flowsPane.isSetup) {
                    flowsPane.listView.getBox(group).setPseudoClassState("flow-group-hover", false)
                }
            }
            val entry = lines.entries.find { (_, l) -> (l.startY - ev.y).absoluteValue < 3.0 }
            if (entry == null) pane.children.remove(tooltip)
            else {
                val (group, line) = entry
                line.strokeWidth = 5.0
                if (tooltip !in pane.children) pane.children.add(tooltip)
                tooltip.text = group.name.now
                tooltip.relocate(ev.x + 12.0, ev.y + 12.0)
                val flowsPane = audioFlowsPane
                if (flowsPane.isShowing.now) {
                    flowsPane.listView.getBox(group).setPseudoClassState("flow-group-hover", true)
                }
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
        line.addEventHandler(MouseEvent.MOUSE_CLICKED) { ev ->
            if (ev.button == MouseButton.SECONDARY) {
                showFlowGroup(group)
            }
        }
    }

    fun showFlowGroup(group: AudioFlowGroup) {
        val flowsPane = audioFlowsPane
        flowsPane.setShowing(true)
        val box = flowsPane.listView.getBox(group)
        if (box.isCollapsed.now) box.toggleExpanded()
        flowsPane.listView.select(group)
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
            startDragEvent = MouseEvent.MOUSE_PRESSED,
            onPressed = { ev ->
                if (ev.modifiers.isNotEmpty()) false
                else {
                    pos = group.yPosition.now
                    line.startYProperty().unbind()
                    line.endYProperty().unbind()
                    true
                }
            },
            onReleased = {
                group.yPosition.now = pos
                VariableEdit.updateVariable(group.yPosition, pos, group.context[UndoManager], "Move flow group")
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

    override fun removed(obj: AudioFlowGroup, idx: Int) {
        val line = lines.remove(obj)
        if (line == null) {
            Logger.warn("No line found for flow group $obj", Logger.Category.Score)
            return
        }
        pane.children.remove(line)
    }

    fun getFlowLine(group: AudioFlowGroup): Line = lines[group] ?: error("No line found for flow group $group")

    companion object : PublicProperty<FlowGroupManager> by publicProperty("FlowGroupLines")
}