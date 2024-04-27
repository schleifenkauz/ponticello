package xenakis.ui

import javafx.geometry.Bounds
import javafx.scene.Node
import javafx.scene.input.MouseEvent
import xenakis.impl.Point

fun Node.setupDragging(relocateBy: (ev: MouseEvent, old: Bounds, dx: Double, dy: Double) -> Unit) {
    var dragStart: Point? = null
    var oldBounds: Bounds? = null
    setOnMouseDragged { ev ->
        val start = dragStart
        if (start == null) {
            dragStart = Point(ev.screenX, ev.screenY)
            oldBounds = boundsInParent
        } else {
            val dx = ev.screenX - start.x
            val dy = ev.screenY - start.y
            relocateBy(ev, oldBounds!!, dx, dy)
        }
        ev.consume()
    }
    setOnMouseReleased { ev ->
        dragStart = null
        oldBounds = null
        ev.consume()
    }
}