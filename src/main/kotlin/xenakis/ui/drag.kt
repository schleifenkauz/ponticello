package xenakis.ui

import hextant.undo.UndoManager
import javafx.geometry.BoundingBox
import javafx.geometry.Bounds
import javafx.scene.Cursor
import javafx.scene.Node
import javafx.scene.input.MouseEvent
import javafx.scene.layout.Region
import xenakis.impl.Point
import xenakis.ui.ToolSelector.Tool
import kotlin.math.absoluteValue

fun Node.setupDragging(
    onPressed: (ev: MouseEvent) -> Unit = {},
    onReleased: (ev: MouseEvent) -> Unit = {},
    relocateBy: (ev: MouseEvent, start: Point, old: Bounds, dx: Double, dy: Double) -> Unit
) {
    var dragStart: Point? = null
    var localStart: Point? = null
    var oldBounds: Bounds? = null
    addEventHandler(MouseEvent.MOUSE_PRESSED) { ev ->
        onPressed(ev)
        ev.consume()
    }
    addEventHandler(MouseEvent.MOUSE_DRAGGED) { ev ->
        val start = dragStart
        if (start == null) {
            dragStart = Point(ev.screenX, ev.screenY)
            localStart = Point(ev.x, ev.y)
            oldBounds = boundsInParent
        } else {
            val dx = ev.screenX - start.x
            val dy = ev.screenY - start.y
            relocateBy(ev, localStart!!, oldBounds!!, dx, dy)
        }
        ev.consume()
    }
    addEventHandler(MouseEvent.MOUSE_RELEASED) { ev ->
        onReleased(ev)
        dragStart = null
        oldBounds = null
        localStart = null
        ev.consume()
    }
}

fun Region.setupDraggingAndResizing(
    pane: ScorePane,
    canUserChangeWidth: Boolean, canUserChangeHeight: Boolean, tool: Tool,
    drag: (x: Double, y: Double) -> Unit,
    resize: (Bounds, Double, Double, Cursor, MouseEvent) -> Unit,
    startDrag: (MouseEvent, Cursor) -> Unit = { _, _ -> },
    finishDrag: (MouseEvent, Cursor) -> Unit = { _, _ -> }
) {
    val context = pane.context
    val toolSelector = context[XenakisUI].toolSelector
    configureResizeCursor(canUserChangeWidth, canUserChangeHeight, toolSelector, tool)
    var dragStart: Point? = null
    var oldBounds: Bounds? = null
    addEventHandler(MouseEvent.MOUSE_PRESSED) { ev ->
        if (toolSelector.selected.value != tool) return@addEventHandler
        if (dragStart == null) {
            oldBounds = BoundingBox(layoutX, layoutY, width, height)
            dragStart = Point(ev.screenX, ev.screenY)
            startDrag(ev, cursor)
            if (isResizeCursor(cursor)) {
                context[UndoManager].beginCompoundEdit("Resize object")
            } else context[UndoManager].beginCompoundEdit("Move object")
        }
        ev.consume()
    }
    addEventHandler(MouseEvent.MOUSE_DRAGGED) { ev ->
        if (toolSelector.selected.value != tool) return@addEventHandler
        val start = dragStart ?: return@addEventHandler
        val dx = ev.screenX - start.x
        val dy = ev.screenY - start.y
        if (isResizeCursor(cursor)) {
            resize(oldBounds!!, dx, dy, cursor, ev)
        } else {
            val x = oldBounds!!.minX + dx
            val y = oldBounds!!.minY + dy
            drag(x, y)
        }
        ev.consume()
    }
    addEventHandler(MouseEvent.MOUSE_RELEASED) { ev ->
        if (toolSelector.selected.value != tool) return@addEventHandler
        if (dragStart != null) {
            if (ev.screenX != dragStart!!.x || ev.screenY != dragStart!!.y) {
                finishDrag(ev, cursor)
            }
            dragStart = null
            oldBounds = null
            context[UndoManager].finishCompoundEdit()
        }
        ev.consume()
    }
}

private fun Region.configureResizeCursor(
    canUserChangeWidth: Boolean, canUserChangeHeight: Boolean,
    toolSelector: ToolSelector, tool: Tool
) {
    setOnMouseEntered { ev ->
        if (!ev.isPrimaryButtonDown && toolSelector.selected.value == tool) {
            updateCursor(ev.x, ev.y, canUserChangeWidth, canUserChangeHeight)
        }
    }
    setOnMouseMoved { ev ->
        if (!ev.isPrimaryButtonDown && toolSelector.selected.value == tool) {
            updateCursor(ev.x, ev.y, canUserChangeWidth, canUserChangeHeight)
        }
    }
    setOnMouseExited { ev ->
        if (!ev.isPrimaryButtonDown) cursor = Cursor.DEFAULT
    }
}

private fun Region.updateCursor(
    x: Double, y: Double,
    canUserChangeWidth: Boolean, canUserChangeHeight: Boolean,
) {
    val tx = 5
    val ty = 5
    val dx = (x - prefWidth).absoluteValue
    val dy = (y - prefHeight).absoluteValue
    cursor = when {
        x.absoluteValue < tx && y.absoluteValue < ty && canUserChangeHeight && canUserChangeWidth -> Cursor.NW_RESIZE
        x.absoluteValue < tx && dy.absoluteValue < ty && canUserChangeHeight && canUserChangeWidth -> Cursor.SW_RESIZE
        dx < tx && y.absoluteValue < ty && canUserChangeHeight && canUserChangeWidth -> Cursor.NE_RESIZE
        dx < tx && dy < ty && canUserChangeHeight && canUserChangeWidth -> Cursor.SE_RESIZE
        x.absoluteValue < tx && canUserChangeWidth -> Cursor.W_RESIZE
        dx < tx && canUserChangeWidth -> Cursor.E_RESIZE
        y.absoluteValue < ty && canUserChangeHeight -> Cursor.N_RESIZE
        dy < ty && canUserChangeHeight -> Cursor.S_RESIZE
        else -> Cursor.DEFAULT
    }
}

private fun isResizeCursor(cursor: Cursor?) = cursor.toString().endsWith("RESIZE")
