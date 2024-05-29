package xenakis.ui

import javafx.geometry.Bounds
import javafx.scene.Node
import javafx.scene.input.MouseEvent
import xenakis.impl.Point

fun Node.setupDragging(
    onPressed: () -> Unit = {},
    onReleased: () -> Unit = {},
    relocateBy: (ev: MouseEvent, start: Point, old: Bounds, dx: Double, dy: Double) -> Unit
) {
    var dragStart: Point? = null
    var localStart: Point? = null
    var oldBounds: Bounds? = null
    addEventHandler(MouseEvent.MOUSE_PRESSED) { ev ->
        onPressed()
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
        onReleased()
        dragStart = null
        oldBounds = null
        localStart = null
        ev.consume()
    }
}