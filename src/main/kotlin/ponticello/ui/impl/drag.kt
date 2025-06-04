package ponticello.ui.impl

import fxutils.isResizeCursor
import fxutils.setupDraggingAndResizing
import fxutils.undo.UndoManager
import hextant.context.Context
import javafx.geometry.Bounds
import javafx.scene.Cursor
import javafx.scene.input.MouseEvent
import javafx.scene.layout.Region

fun Region.setupDraggingAndResizing(
    context: Context,
    canUserChangeWidth: Boolean, canUserChangeHeight: Boolean,
    resizeDescription: (MouseEvent) -> String = { "Resize object" },
    moveDescription: (MouseEvent) -> String = { "Move object" },
    startDrag: (MouseEvent, Cursor) -> Boolean = { _, _ -> true },
    drag: (x: Double, y: Double) -> Unit,
    resize: (Bounds, Double, Double, Cursor, MouseEvent) -> Unit,
    finishDrag: (MouseEvent, Cursor) -> Unit = { _, _ -> },
) {
    setupDraggingAndResizing(
        canUserChangeWidth, canUserChangeHeight, threshold = 5.0,
        drag, resize,
        startDrag = { ev, cursor ->
            if (startDrag(ev, cursor)) {
                if (isResizeCursor(cursor)) {
                    context[UndoManager].beginCompoundEdit(resizeDescription(ev))
                } else {
                    context[UndoManager].beginCompoundEdit(moveDescription(ev))
                }
                true
            } else false
        },
        finishDrag = { ev, cursor ->
            finishDrag(ev, cursor)
            context[UndoManager].finishCompoundEdit()
        }
    )
}