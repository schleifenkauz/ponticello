package xenakis.ui

import hextant.context.Context
import hextant.context.createControl
import hextant.fx.Stylesheets
import hextant.serial.makeRoot
import javafx.collections.FXCollections
import javafx.geometry.Bounds
import javafx.scene.control.Label
import javafx.scene.input.KeyCode
import javafx.scene.input.KeyEvent
import javafx.scene.input.MouseEvent
import javafx.scene.layout.Pane
import javafx.scene.paint.Color
import javafx.stage.StageStyle
import org.controlsfx.control.PropertySheet
import org.controlsfx.property.BeanProperty
import xenakis.impl.Arrow
import xenakis.impl.Point
import xenakis.model.AudioFlowGraph
import xenakis.sc.Bus
import xenakis.sc.Rate
import xenakis.sc.editor.ScExprExpander
import java.beans.PropertyDescriptor
import kotlin.math.sign

class AudioFlowGraphEditor(private val graph: AudioFlowGraph, private val context: Context) : Pane() {
    private var sourceBus: AudioFlowGraph.BusObject? = null
    private var flowArrow: Arrow? = null
    private var dragStart: Point? = null
    private var oldBounds: Bounds? = null
    private val busLabels = mutableMapOf<AudioFlowGraph.BusObject, Label>()
    private val flowArrows = mutableMapOf<AudioFlowGraph.AudioFlow, Arrow>()

    init {
        styleClass("audio-flow-graph")
        for (obj in graph.busses) {
            paintBusObject(obj)
        }
        for (flow in graph.flows) {
            paintFlow(flow)
        }
        addEventHandler(KeyEvent.KEY_RELEASED) { ev ->
            if (ev.code == KeyCode.ESCAPE) {
                sourceBus = null
                if (flowArrow != null) children.remove(flowArrow)
                flowArrow = null
                ev.consume()
            }
        }
        addEventHandler(MouseEvent.MOUSE_CLICKED) { ev ->
            if (ev.isShiftDown) {
                val bus = Bus("new_bus", Rate.Audio, 2, Color.WHITE)
                val confirmed = showDetailEditor(bus)
                if (confirmed) {
                    val obj = AudioFlowGraph.BusObject(bus, ev.x, ev.y)
                    graph.addBus(obj)
                    paintBusObject(obj)
                }
                ev.consume()
            }
        }
        addEventHandler(MouseEvent.MOUSE_MOVED) { ev ->
            if (flowArrow != null) {
                val arr = flowArrow!!
                val dir = (arr.end.y - arr.start.y).sign
                arr.setEnd(ev.sceneX, ev.sceneY - (DIST_MOUSE_TO_HEAD * dir))
                ev.consume()
            }
        }
    }

    private fun paintBusObject(obj: AudioFlowGraph.BusObject) {
        val label = Label(obj.bus.name).styleClass("bus-object")
        label.textFill = obj.bus.associatedColor
        label.isFocusTraversable = true
        label.relocate(obj.x, obj.y)
        label.addEventHandler(MouseEvent.ANY) { ev ->
            when {
                ev.eventType == MouseEvent.MOUSE_DRAGGED && !ev.isShiftDown -> drag(ev, label, obj)
                ev.eventType == MouseEvent.MOUSE_RELEASED -> releaseDrag(ev)
                ev.eventType == MouseEvent.MOUSE_CLICKED && ev.isShiftDown && sourceBus == null ->
                    startNewFlowFrom(obj, ev)

                ev.eventType == MouseEvent.MOUSE_CLICKED && sourceBus != null -> finishFlowTo(obj, ev)
                ev.eventType == MouseEvent.MOUSE_CLICKED && ev.clickCount >= 2 -> {
                    editBusDetails(obj)
                    ev.consume()
                }

                ev.eventType == MouseEvent.MOUSE_CLICKED -> {
                    label.requestFocus()
                    ev.consume()
                }

            }
        }
        label.addEventHandler(KeyEvent.KEY_RELEASED) { ev ->
            if (ev.code == KeyCode.DELETE) {
                removeBus(obj)
                ev.consume()
            }
        }
        children.add(label)
        busLabels[obj] = label
    }

    private fun finishFlowTo(obj: AudioFlowGraph.BusObject, ev: MouseEvent) {
        val source = sourceBus!!
        val arrow = flowArrow!!
        val flow = AudioFlowGraph.AudioFlow(source, obj, null)
        graph.addFlow(flow)
        setupFlowArrow(arrow, flow)
        sourceBus = null
        flowArrow = null
        ev.consume()
    }

    private fun startNewFlowFrom(obj: AudioFlowGraph.BusObject, ev: MouseEvent) {
        sourceBus = obj
        val arrow = Arrow(ev.sceneX, ev.sceneY, ev.sceneX, ev.sceneY)
        arrow.strokeWidth = 2.5
        children.add(arrow)
        flowArrow = arrow
        ev.consume()
    }

    private fun releaseDrag(ev: MouseEvent) {
        dragStart = null
        oldBounds = null
        ev.consume()
    }

    private fun drag(
        ev: MouseEvent,
        label: Label,
        obj: AudioFlowGraph.BusObject
    ) {
        val start = dragStart
        if (start == null) {
            dragStart = Point(ev.screenX, ev.screenY)
            oldBounds = label.boundsInParent
        } else {
            val dx = ev.screenX - start.x
            val dy = ev.screenY - start.y
            val old = oldBounds!!
            obj.x = (old.minX + dx).coerceIn(0.0, width - label.width)
            obj.y = (old.minY + dy).coerceIn(0.0, height - label.height)
            label.relocate(obj.x, obj.y)
            for (flow in graph.associatedFlows(obj)) {
                val arrow = flowArrows[flow]!!
                if (obj == flow.source) {
                    arrow.setStart(obj.x, obj.y)
                } else {
                    arrow.setEnd(obj.x, obj.y)
                }
            }
        }
    }

    private fun setupFlowArrow(arrow: Arrow, flow: AudioFlowGraph.AudioFlow) {
        arrow.styleClass("flow-arrow")
        arrow.setOnMouseClicked { editFlowDetails(flow) }
        flowArrows[flow] = arrow
    }

    private fun removeBus(obj: AudioFlowGraph.BusObject) {
        val label = busLabels.remove(obj) ?: error("$obj missing in graph?")
        children.remove(label)
        graph.removeBus(obj)
        for (flow in graph.associatedFlows(obj)) {
            removeFlow(flow)
        }
    }

    private fun removeFlow(flow: AudioFlowGraph.AudioFlow) {
        graph.removeFlow(flow)
        val arrow = flowArrows.remove(flow) ?: error("$flow missing in graph?")
        children.remove(arrow)
    }

    private fun editFlowDetails(flow: AudioFlowGraph.AudioFlow) {
        val expr = ScExprExpander(context)
        expr.makeRoot()
        val view = context.createControl(expr)
        val window = view.makeWindow("Flow configuration", context, StageStyle.DECORATED)
        context[Stylesheets].manage(window.scene)
        window.showAndWait()
    }

    private fun editBusDetails(obj: AudioFlowGraph.BusObject) {
        val copy = obj.bus.copy()
        val confirmed = showDetailEditor(copy)
        if (confirmed) {
            obj.bus.copyFrom(copy)
            val label = busLabels[obj]!!
            label.textFill = obj.bus.associatedColor
            label.text = obj.bus.name
        }
    }

    private fun showDetailEditor(obj: Bus): Boolean {
        val items = Bus.PROPERTY_NAMES.map { name ->
            BeanProperty(obj, PropertyDescriptor(name, Bus::class.java))
        }
        val sheet = PropertySheet(FXCollections.observableList(items))
        sheet.isModeSwitcherVisible = false
        sheet.isSearchBoxVisible = false
        sheet.setPrefSize(300.0, 145.0)
        return showDialog(sheet, context) { true } ?: false
    }

    private fun paintFlow(flow: AudioFlowGraph.AudioFlow) {

    }

    companion object {
        private const val DIST_MOUSE_TO_HEAD = 10.0
    }
}