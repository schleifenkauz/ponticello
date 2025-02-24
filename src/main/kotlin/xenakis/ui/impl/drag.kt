package xenakis.ui.impl

import hextant.context.Context
import hextant.undo.UndoManager
import javafx.geometry.BoundingBox
import javafx.geometry.Bounds
import javafx.geometry.Point2D
import javafx.scene.Cursor
import javafx.scene.Node
import javafx.scene.input.MouseEvent
import javafx.scene.layout.Region
import xenakis.ui.actions.Tool
import xenakis.ui.launcher.XenakisMainActivity
import kotlin.math.absoluteValue

fun Node.setupDragging(
    context: Context, dragTool: Tool,
    defaultCursor: Cursor = Cursor.OPEN_HAND, dragCursor: Cursor = Cursor.CLOSED_HAND,
    onPressed: (ev: MouseEvent) -> Unit = {},
    onReleased: (ev: MouseEvent) -> Unit = {},
    relocateBy: (ev: MouseEvent, start: Point2D, old: Bounds, dx: Double, dy: Double) -> Unit
) {
    var dragStart: Point2D? = null
    var localStart: Point2D? = null
    var oldBounds: Bounds? = null
    val toolSelector = context[XenakisMainActivity].toolSelector
    cursor = defaultCursor
    addEventHandler(MouseEvent.ANY) { ev ->
        if (toolSelector.selected != dragTool) return@addEventHandler
        when (ev.eventType) {
            MouseEvent.MOUSE_PRESSED -> {
                cursor = dragCursor
                onPressed(ev)
            }

            MouseEvent.MOUSE_DRAGGED -> {
                val start = dragStart
                if (start == null) {
                    dragStart = Point2D(ev.screenX, ev.screenY)
                    localStart = Point2D(ev.x, ev.y)
                    oldBounds = boundsInParent
                } else {
                    val dx = ev.screenX - start.x
                    val dy = ev.screenY - start.y
                    relocateBy(ev, localStart!!, oldBounds!!, dx, dy)
                }
            }

            MouseEvent.MOUSE_RELEASED -> {
                cursor = defaultCursor
                onReleased(ev)
                dragStart = null
                oldBounds = null
                localStart = null
            }

            else -> return@addEventHandler
        }
        ev.consume()
    }
}

fun Region.setupDraggingAndResizing(
    context: Context,
    canUserChangeWidth: Boolean,
    canUserChangeHeight: Boolean, moveTool: Tool,
    resizeTool: Tool, drag: (x: Double, y: Double) -> Unit,
    resize: (Bounds, Double, Double, Cursor, MouseEvent) -> Unit,
    startDrag: (MouseEvent, Cursor) -> Boolean = { _, _ -> true },
    finishDrag: (MouseEvent, Cursor) -> Unit = { _, _ -> }
) {
    cursor = Cursor.OPEN_HAND
    val toolSelector = context[XenakisMainActivity].toolSelector
    var dragStart: Point2D? = null
    var oldBounds: Bounds? = null
    addEventHandler(MouseEvent.ANY) { ev ->
        if (toolSelector.selected !in setOf(moveTool, resizeTool)) return@addEventHandler
        when (ev.eventType) {
            MouseEvent.MOUSE_PRESSED -> {
                if (dragStart == null && cursor != null) {
                    oldBounds = BoundingBox(layoutX, layoutY, width, height)
                    dragStart = Point2D(ev.screenX, ev.screenY)
                    startDrag(ev, cursor)
                    if (isResizeCursor(cursor)) {
                        context[UndoManager].beginCompoundEdit("Resize object")
                    } else {
                        context[UndoManager].beginCompoundEdit("Move object")
                    }
                }
            }

            MouseEvent.MOUSE_DRAGGED -> {
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
            }

            MouseEvent.MOUSE_MOVED -> {
                cursor = getCursor(
                    toolSelector.selected, moveTool, resizeTool,
                    ev, canUserChangeWidth, canUserChangeHeight, ev.isPrimaryButtonDown
                )
                return@addEventHandler
            }

            MouseEvent.MOUSE_RELEASED -> {
                if (dragStart != null) {
                    if (ev.screenX != dragStart!!.x || ev.screenY != dragStart!!.y) {
                        finishDrag(ev, cursor)
                    }
                    dragStart = null
                    oldBounds = null
                    context[UndoManager].finishCompoundEdit()
                }
            }

            else -> return@addEventHandler
        }
        ev.consume()
    }
}

private fun Region.getCursor(
    selectedTool: Tool, moveTool: Tool, resizeTool: Tool,
    ev: MouseEvent, canUserChangeWidth: Boolean, canUserChangeHeight: Boolean, closeHand: Boolean
): Cursor {
    val x = ev.x
    val y = ev.y
    val tx = 5
    val ty = 5
    val dx = (x - prefWidth).absoluteValue
    val dy = (y - prefHeight).absoluteValue
    return when {
        selectedTool == moveTool -> if (closeHand) Cursor.CLOSED_HAND else Cursor.OPEN_HAND
        selectedTool != resizeTool -> if (closeHand) cursor else Cursor.DEFAULT
        x.absoluteValue < tx && y.absoluteValue < ty && canUserChangeHeight && canUserChangeWidth -> Cursor.NW_RESIZE
        x.absoluteValue < tx && dy.absoluteValue < ty && canUserChangeHeight && canUserChangeWidth -> Cursor.SW_RESIZE
        dx < tx && y.absoluteValue < ty && canUserChangeHeight && canUserChangeWidth -> Cursor.NE_RESIZE
        dx < tx && dy < ty && canUserChangeHeight && canUserChangeWidth -> Cursor.SE_RESIZE
        x.absoluteValue < tx && canUserChangeWidth -> Cursor.W_RESIZE
        dx < tx && canUserChangeWidth -> Cursor.E_RESIZE
        y.absoluteValue < ty && canUserChangeHeight -> Cursor.N_RESIZE
        dy < ty && canUserChangeHeight -> Cursor.S_RESIZE
        closeHand -> Cursor.CLOSED_HAND
        else -> Cursor.OPEN_HAND
    }
}

private fun isResizeCursor(cursor: Cursor?) = cursor.toString().endsWith("RESIZE")
