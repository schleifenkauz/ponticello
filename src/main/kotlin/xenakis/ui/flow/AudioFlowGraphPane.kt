package xenakis.ui.flow

import bundles.PublicProperty
import bundles.publicProperty
import bundles.set
import fxutils.*
import hextant.context.Context
import hextant.undo.UndoManager
import javafx.geometry.Bounds
import javafx.geometry.HorizontalDirection
import javafx.geometry.HorizontalDirection.LEFT
import javafx.geometry.HorizontalDirection.RIGHT
import javafx.geometry.Point2D
import javafx.geometry.VerticalDirection
import javafx.geometry.VerticalDirection.DOWN
import javafx.geometry.VerticalDirection.UP
import javafx.scene.control.Label
import javafx.scene.input.MouseButton
import javafx.scene.input.MouseEvent
import javafx.scene.layout.Pane
import reaktive.value.now
import xenakis.impl.Point
import xenakis.model.Logger
import xenakis.model.flow.AudioFlow
import xenakis.model.flow.AudioFlows
import xenakis.model.obj.BusObject
import xenakis.model.registry.BusRegistry
import xenakis.model.registry.ObjectRegistry
import xenakis.ui.controls.NamePrompt
import xenakis.ui.impl.Arrow
import xenakis.ui.impl.invert
import xenakis.ui.launcher.XenakisLauncher.Companion.currentProject
import kotlin.math.absoluteValue
import kotlin.math.sign

class AudioFlowGraphPane(
    private val graph: AudioFlows,
    private val context: Context
) : Pane(), AudioFlows.Listener, ObjectRegistry.Listener<BusObject> {
    private var sourceBus: BusObject? = null
    private var flowArrow: Arrow? = null
    private var dragStart: Point2D? = null
    private var oldBounds: Bounds? = null
    private val busLabels = mutableListOf<Label>()
    private val flowArrows = mutableListOf<Pair<AudioFlow, Arrow>>()

    init {
        context[AudioFlowGraphPane] = this
        styleClass("audio-flow-graph")
        sceneProperty().addListener { _, _, sc ->
            sc?.windowProperty()?.addListener { _, _, window ->
                window.setOnShown {
                    for ((flow, arrow) in flowArrows)
                        repositionArrow(arrow, flow)
                }
            }
        }
        registerShortcuts()
        newFlowArrowFollowMouse()
        allowDroppingBusObjects()
        graph.context[BusRegistry].addListener(this)
        graph.addListener(this)
    }

    /*
    * Event listeners
    * */

    private fun allowDroppingBusObjects() {
//        setOnMouseClicked { ev ->
//            if (ev.isAltDown) {
//                val busList = SearchableBusListView(context[BusRegistry], "Add bus node")
//                busList.removedOptions.addAll(graph.nodes.map { node -> node.busRef.get<BusObject>() })
//                val anchor = Point2D(ev.screenX, ev.screenY)
//                busList.showPopup(context, anchor) { bus ->
//                    graph.addNode(bus.reference(), Point2D(ev.x, ev.y))
//                }
//            }
//        }
//        addEventHandler(DragEvent.DRAG_OVER) { ev ->
//            if (ev.dragboard.hasContent(BusObject.DATA_FORMAT)) {
//                ev.acceptTransferModes(TransferMode.LINK)
//            }
//            ev.consume()
//        }
//        addEventHandler(DragEvent.DRAG_ENTERED) { ev ->
//            if (ev.dragboard.hasContent(BusObject.DATA_FORMAT)) {
//                setPseudoClassState("drop-possible", true)
//            }
//            ev.consume()
//        }
//        addEventHandler(DragEvent.DRAG_EXITED) { ev ->
//            setPseudoClassState("drop-possible", false)
//            ev.consume()
//        }
//        addEventHandler(DragEvent.DRAG_DROPPED) { ev ->
//            val busName = ev.dragboard.getContent(BusObject.DATA_FORMAT) as? String ?: return@addEventHandler
//            val bus = context[currentProject].busses.get(busName).reference()
//            if (!graph.addNode(bus, Point2D(ev.x, ev.y))) {
//                Logger.error("Cannot add same bus twice in audio flow graph")
//            }
//            ev.consume()
//        }
    }

    private fun registerShortcuts() = registerShortcuts {
        on("Ctrl+S") { sync() }
        on("ESCAPE") {
            requestFocus()
            sourceBus = null
            if (flowArrow != null) children.remove(flowArrow)
            flowArrow = null
        }
    }

    private fun newFlowArrowFollowMouse() {
        addEventHandler(MouseEvent.MOUSE_MOVED) { ev ->
            if (flowArrow != null) {
                val arr = flowArrow!!
                val dir = (arr.end.y - arr.start.y).sign
                arr.setEnd(ev.sceneX, ev.sceneY - (DIST_MOUSE_TO_HEAD * dir))
                ev.consume()
            }
        }
    }

    override fun added(obj: BusObject, idx: Int) {
        val label = label(obj.name).styleClass("bus-node")
        label.isFocusTraversable = true
        label.registerShortcuts {
            on("F2") {
                val name = NamePrompt(context[BusRegistry], "Rename bus", obj.name.now)
                    .showDialog(label) ?: return@on
                obj.rename(name)
            }
        }
        val position = obj.positionInGraph.now
        label.relocate(position.x, position.y)
        setupEvents(obj, label)
        children.add(label)
        busLabels.add(label)
    }

    override fun addedFlow(flow: AudioFlow, index: Int) {
        val arrow = Arrow() styleClass "flow-arrow"
        arrow.setOnMouseClicked { ev ->
            if (ev.button == MouseButton.SECONDARY) {
                graph.removeFlow(flow)
            }
            ev.consume()
        }
        flowArrows.add(flow to arrow)
        repositionArrow(arrow, flow)
        children.add(arrow)
    }

    override fun movedNode(node: BusObject) {
        val label = getLabel(node)
        val position = node.positionInGraph.now
        label.relocate(position.x, position.y)
        for (flow in graph.flowsFromAndTo(node)) {
            val arrow = flowArrows.find { (f, _) -> f == flow }!!.second
            repositionArrow(arrow, flow)
        }
    }

    private fun repositionArrow(arrow: Arrow, flow: AudioFlow) {
        for (source in flow.getInputs()) {
            for (sink in flow.getOutputs()) {
                val sourceLbl = getLabel(source)
                val targetLbl = getLabel(sink)
                val targetPos = sink.positionInGraph.now
                val sourcePos = source.positionInGraph.now
                val deltaX = targetPos.x - sourcePos.x
                val deltaY = targetPos.y - sourcePos.y
                val slopeH = deltaX / deltaY.absoluteValue
                val slopeV = deltaY / deltaX.absoluteValue
                val hDir = if (slopeH < -0.1) LEFT else if (slopeH > 0.1) RIGHT else null
                val vDir = if (slopeV < -0.1) DOWN else if (slopeV > 0.1) UP else null
                arrow.startX = xPos(hDir?.invert(), sourceLbl)
                arrow.startY = yPos(vDir?.invert(), sourceLbl)
                arrow.endX = xPos(hDir, targetLbl)
                arrow.endY = yPos(vDir, targetLbl)
            }
        }
    }

    private fun xPos(hDir: HorizontalDirection?, target: Label) = when (hDir) {
        RIGHT -> target.boundsInParent.minX
        LEFT -> target.boundsInParent.maxX
        else -> target.boundsInParent.middleX
    }

    private fun yPos(vDir: VerticalDirection?, source: Label) = when (vDir) {
        DOWN -> source.boundsInParent.maxY
        UP -> source.boundsInParent.minY
        else -> source.boundsInParent.middleY
    }

    private fun getLabel(node: BusObject) =
        busLabels.find { l -> l.text == node.name.now }
            ?: error("Bus ${node.name.now} not displayed in AudioFlowGraphPane")

    /*
    * Graph node event listeners
    * */

    private fun setupEvents(node: BusObject, label: Label) {
        label.addEventHandler(MouseEvent.ANY) { ev ->
            when {
                ev.eventType == MouseEvent.MOUSE_DRAGGED && !ev.isAltDown -> drag(ev, label, node)
                ev.eventType == MouseEvent.MOUSE_RELEASED -> releaseDrag()
                ev.eventType == MouseEvent.MOUSE_CLICKED && ev.isAltDown && sourceBus == null ->
                    startNewFlowFrom(node, ev)

                ev.eventType == MouseEvent.MOUSE_CLICKED && sourceBus != null -> finishFlowTo(node)
                ev.eventType == MouseEvent.MOUSE_CLICKED -> label.requestFocus()
                else -> return@addEventHandler
            }
            ev.consume()
        }
//        label.addEventHandler(KeyEvent.KEY_RELEASED) { ev ->
//            if (ev.code == KeyCode.DELETE) {
//                graph.removeNode(node)
//                ev.consume()
//            }
//        }
    }

    private fun releaseDrag() {
        dragStart = null
        oldBounds = null
        if (context[UndoManager].accumulatesCompoundEdit)
            context[UndoManager].finishCompoundEdit("Move audio flow node")
    }

    private fun drag(ev: MouseEvent, label: Label, obj: BusObject) {
        val start = dragStart
        if (start == null) {
            dragStart = Point2D(ev.screenX, ev.screenY)
            oldBounds = label.boundsInParent
            context[UndoManager].beginCompoundEdit("Move audio flow node")
        } else {
            val dx = ev.screenX - start.x
            val dy = ev.screenY - start.y
            val old = oldBounds!!
            val position = Point(
                (old.minX + dx).coerceIn(0.0, width - label.width),
                (old.minY + dy).coerceIn(0.0, height - label.height)
            )
            graph.move(obj, position)
        }
    }

    private fun startNewFlowFrom(obj: BusObject, ev: MouseEvent) {
        sourceBus = obj
        val arrow = Arrow(ev.sceneX, ev.sceneY, ev.sceneX, ev.sceneY)
        arrow.strokeWidth = 2.5
        children.add(arrow)
        flowArrow = arrow
    }

    private fun finishFlowTo(target: BusObject) {
        val source = sourceBus!!
        val arrow = flowArrow!!
        children.remove(arrow)
        sourceBus = null
        flowArrow = null
        graph.addSendFlow(source, target)
    }

    override fun removed(obj: BusObject, idx: Int) {
        val label = getLabel(obj)
        busLabels.remove(label)
        children.remove(label)
    }

    override fun removedFlow(flow: AudioFlow) {
        val pair = flowArrows.find { (f, _) -> f == flow }!!
        flowArrows.remove(pair)
        children.remove(pair.second)
    }

    private fun sync() {
        graph.forceSync()
        context[currentProject].save(graph)
        Logger.confirm("Updated Audio flow graph", Logger.Category.AudioFlow)
    }

    companion object : PublicProperty<AudioFlowGraphPane> by publicProperty("audio-flow-graph-editor") {
        private const val DIST_MOUSE_TO_HEAD = 10.0
    }
}