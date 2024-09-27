package xenakis.ui

import bundles.PublicProperty
import bundles.publicProperty
import bundles.set
import hextant.context.Context
import hextant.fx.initHextantScene
import hextant.fx.registerShortcuts
import hextant.fx.runFXWithTimeout
import hextant.fx.shortcut
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
import javafx.scene.input.*
import javafx.scene.layout.Pane
import reaktive.value.now
import xenakis.impl.Arrow
import xenakis.impl.Point
import xenakis.model.AudioFlowGraph
import xenakis.model.BusObject
import xenakis.model.BusRegistry
import xenakis.model.ObjectReference
import xenakis.ui.XenakisController.Companion.currentProject
import xenakis.ui.prompt.NamePrompt
import kotlin.math.absoluteValue
import kotlin.math.sign

class AudioFlowGraphPane(
    private val graph: AudioFlowGraph,
    private val context: Context
) : Pane(), AudioFlowGraph.View {
    private var sourceBus: AudioFlowGraph.BusNode? = null
    private var flowArrow: Arrow? = null
    private var dragStart: Point? = null
    private var oldBounds: Bounds? = null
    private val busLabels = mutableListOf<Label>()
    private val flowArrows = mutableListOf<Pair<AudioFlowGraph.AudioFlow, Arrow>>()
    private val flowDetailWindows = mutableMapOf<AudioFlowGraph.AudioFlow, SubWindow>()

    init {
        context[AudioFlowGraphPane] = this
        styleClass("audio-flow-graph")
        for (obj in graph.nodes) addedNode(obj)
        for (flow in graph.flows) addedFlow(flow)
        sceneProperty().addListener { _ ->
            runFXWithTimeout(50) {
                for ((flow, arrow) in flowArrows)
                    repositionArrow(arrow, flow)
            }
        }
        registerShortcuts()
        newFlowArrowFollowMouse()
        allowDroppingBusObjects()
        graph.views.addListener(this)
    }

    /*
    * Event listeners
    * */

    private fun allowDroppingBusObjects() {
        setOnMouseClicked { ev ->
            if (ev.isAltDown) {
                val busList = SearchableBusListView(context[BusRegistry])
                busList.removedOptions.addAll(graph.nodes.map { node -> node.ref.get<BusObject>() })
                val anchor = Point2D(ev.screenX, ev.screenY)
                busList.showPopup(context, "Add bus node", anchor) { bus ->
                    graph.addNode(bus.createReference(), Point(ev.x, ev.y))
                }
            }
        }
        addEventHandler(DragEvent.DRAG_OVER) { ev ->
            if (ev.dragboard.hasContent(BusObject.DATA_FORMAT)) {
                ev.acceptTransferModes(TransferMode.LINK)
            }
            ev.consume()
        }
        addEventHandler(DragEvent.DRAG_ENTERED) { ev ->
            if (ev.dragboard.hasContent(BusObject.DATA_FORMAT)) {
                setPseudoClassState("drop-possible", true)
            }
            ev.consume()
        }
        addEventHandler(DragEvent.DRAG_EXITED) { ev ->
            setPseudoClassState("drop-possible", false)
            ev.consume()
        }
        addEventHandler(DragEvent.DRAG_DROPPED) { ev ->
            val busName = ev.dragboard.getContent(BusObject.DATA_FORMAT) as? String ?: return@addEventHandler
            val bus = context[currentProject].busses.get(busName).createReference()
            if (!graph.addNode(bus, Point(ev.x, ev.y))) {
                alertError("Cannot add same bus twice in audio flow graph")
            }
            ev.consume()
        }
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

    override fun addedNode(node: AudioFlowGraph.BusNode) {
        val label = label(node.bus.name).styleClass("bus-node")
        label.isFocusTraversable = true
        label.registerShortcuts {
            on("S") { ev ->
                val output = ev.isShiftDown
                graph.split(node, output)
            }
            on("F2") {
                val name = NamePrompt(context[BusRegistry], "Rename bus", node.bus.name.now)
                    .showDialog(context) ?: return@on
                node.bus.rename(name)
            }
        }
        label.relocate(node.position.x, node.position.y)
        setupEvents(node, label)
        children.add(label)
        busLabels.add(label)
    }

    override fun addedFlow(flow: AudioFlowGraph.AudioFlow) {
        val arrow = Arrow() styleClass "flow-arrow"
        arrow.setOnMouseClicked { ev ->
            if (ev.button == MouseButton.SECONDARY) {
                graph.removeFlow(flow)
            } else {
                editFlowDetails(flow)
            }
            ev.consume()
        }
        flowArrows.add(flow to arrow)
        repositionArrow(arrow, flow)
        children.add(arrow)
    }

    override fun movedNode(node: AudioFlowGraph.BusNode) {
        val label = getLabel(node.ref)
        label.relocate(node.position.x, node.position.y)
        for (flow in graph.associatedFlows(node)) {
            val arrow = flowArrows.find { (f, _) -> f == flow }!!.second
            repositionArrow(arrow, flow)
        }
    }

    private fun repositionArrow(arrow: Arrow, flow: AudioFlowGraph.AudioFlow) {
        val source = getLabel(flow.source)
        val target = getLabel(flow.target)
        val targetPos = graph.getNode(flow.target).position
        val sourcePos = graph.getNode(flow.source).position
        val deltaX = targetPos.x - sourcePos.x
        val deltaY = targetPos.y - sourcePos.y
        val slopeH = deltaX / deltaY.absoluteValue
        val slopeV = deltaY / deltaX.absoluteValue
        val hDir = if (slopeH < -0.1) LEFT else if (slopeH > 0.1) RIGHT else null
        val vDir = if (slopeV < -0.1) DOWN else if (slopeV > 0.1) UP else null
        arrow.startX = xPos(hDir?.invert(), source)
        arrow.startY = yPos(vDir?.invert(), source)
        arrow.endX = xPos(hDir, target)
        arrow.endY = yPos(vDir, target)
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

    private fun getLabel(node: ObjectReference) =
        busLabels.find { l -> l.text == node.get<BusObject>().name.now }
            ?: error("Bus ${node.get<BusObject>().name.now} not displayed in AudioFlowGraphPane")

    /*
    * Graph node event listeners
    * */

    private fun setupEvents(node: AudioFlowGraph.BusNode, label: Label) {
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
        label.addEventHandler(KeyEvent.KEY_RELEASED) { ev ->
            if (ev.code == KeyCode.DELETE) {
                graph.removeNode(node)
                ev.consume()
            }
        }
    }

    private fun releaseDrag() {
        dragStart = null
        oldBounds = null
        if (context[UndoManager].accumulatesCompoundEdit)
            context[UndoManager].finishCompoundEdit("Move audio flow node")
    }

    private fun drag(ev: MouseEvent, label: Label, obj: AudioFlowGraph.BusNode) {
        val start = dragStart
        if (start == null) {
            dragStart = Point(ev.screenX, ev.screenY)
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

    private fun startNewFlowFrom(obj: AudioFlowGraph.BusNode, ev: MouseEvent) {
        sourceBus = obj
        val arrow = Arrow(ev.sceneX, ev.sceneY, ev.sceneX, ev.sceneY)
        arrow.strokeWidth = 2.5
        children.add(arrow)
        flowArrow = arrow
    }

    private fun finishFlowTo(target: AudioFlowGraph.BusNode) {
        val source = sourceBus!!
        val arrow = flowArrow!!
        children.remove(arrow)
        sourceBus = null
        flowArrow = null
        val flow = graph.addFlow(source, target)
        if (flow == null) {
            val sourceName = source.ref.get<BusObject>().name.now
            val targetName = target.ref.get<BusObject>().name.now
            alertError("Cannot add flow from $sourceName to $targetName")
            return
        }
        editFlowDetails(flow)
    }

    override fun removedNode(node: AudioFlowGraph.BusNode) {
        val label = getLabel(node.ref)
        busLabels.remove(label)
        children.remove(label)
    }

    override fun removedFlow(flow: AudioFlowGraph.AudioFlow) {
        val pair = flowArrows.find { (f, _) -> f == flow }!!
        flowArrows.remove(pair)
        children.remove(pair.second)
    }

    private fun editFlowDetails(flow: AudioFlowGraph.AudioFlow) {
        val window = flowDetailWindows.getOrPut(flow) {
            val source = flow.source.get<BusObject>().name.now
            val target = flow.target.get<BusObject>().name.now
            SubWindow(flow.synth.control, "Audio flow from $source to $target", context).apply {
                width = 1000.0
                height = 1000.0
                scene.initHextantScene(context, applyStyle = false)
                addEventFilter(KeyEvent.KEY_RELEASED) { ev ->
                    if ("Ctrl+S".shortcut.matches(ev)) {
                        sync()
                        ev.consume()
                    }
                }
            }
        }
        window.show()
    }

    private fun sync() {
        graph.updateFlow()
        context[currentProject].save(graph)
        notifyConfirm("Updated Audio flow graph")
    }

    companion object : PublicProperty<AudioFlowGraphPane> by publicProperty("audio-flow-graph-editor") {
        private const val DIST_MOUSE_TO_HEAD = 10.0
    }
}