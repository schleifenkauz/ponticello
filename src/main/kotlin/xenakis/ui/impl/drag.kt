package xenakis.ui.impl

import fxutils.isResizeCursor
import fxutils.setupDraggingAndResizing
import hextant.context.Context
import hextant.undo.UndoManager
import javafx.geometry.Bounds
import javafx.scene.Cursor
import javafx.scene.input.MouseEvent
import javafx.scene.layout.Region

fun Region.setupDraggingAndResizing(
    context: Context,
    canUserChangeWidth: Boolean, canUserChangeHeight: Boolean,
    drag: (x: Double, y: Double) -> Unit, resize: (Bounds, Double, Double, Cursor, MouseEvent) -> Unit,
    startDrag: (MouseEvent, Cursor) -> Boolean = { _, _ -> true },
    finishDrag: (MouseEvent, Cursor) -> Unit = { _, _ -> }
) {
    setupDraggingAndResizing(
        canUserChangeWidth, canUserChangeHeight,
        drag, resize,
        startDrag = { ev, cursor ->
            if (startDrag(ev, cursor)) {
                if (isResizeCursor(cursor)) {
                    context[UndoManager].beginCompoundEdit("Resize object")
                } else {
                    context[UndoManager].beginCompoundEdit("Move object")
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