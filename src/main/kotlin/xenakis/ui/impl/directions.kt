package xenakis.ui.impl

import javafx.geometry.HorizontalDirection
import javafx.geometry.HorizontalDirection.LEFT
import javafx.geometry.HorizontalDirection.RIGHT
import javafx.geometry.VerticalDirection
import javafx.geometry.VerticalDirection.DOWN
import javafx.geometry.VerticalDirection.UP
import javafx.scene.Cursor

data class Direction(
    private val horizontalDirection: HorizontalDirection?,
    private val verticalDirection: VerticalDirection?
) {
    val horizontal get() = horizontalDirection
    val vertical get() = verticalDirection

    val left get() = horizontalDirection == LEFT
    val right get() = horizontalDirection == RIGHT
    val up get() = verticalDirection == UP
    val down get() = verticalDirection == DOWN

    override fun toString(): String {
        val vertical = verticalDirection?.name?.lowercase()
        val horizontal = horizontalDirection?.name?.lowercase()
        return when {
            vertical == null && horizontal == null -> "none"
            vertical == null -> horizontal!!
            horizontal == null -> vertical
            else -> "${vertical}_$horizontal"
        }
    }

    companion object {
        val NONE get() = Direction(null, null)

        fun horizontal(direction: HorizontalDirection) = Direction(direction, null)

        fun vertical(direction: VerticalDirection) = Direction(null, direction)
    }
}

val Cursor.isResizeCursor get() = this.toString().endsWith("RESIZE")
fun Cursor.resizeDirection() = when (this) {
    Cursor.N_RESIZE -> Direction(null, UP)
    Cursor.NW_RESIZE -> Direction(LEFT, UP)
    Cursor.NE_RESIZE -> Direction(RIGHT, UP)
    Cursor.S_RESIZE -> Direction(null, DOWN)
    Cursor.SW_RESIZE -> Direction(LEFT, DOWN)
    Cursor.SE_RESIZE -> Direction(RIGHT, DOWN)
    Cursor.E_RESIZE -> Direction(RIGHT, null)
    Cursor.W_RESIZE -> Direction(LEFT, null)
    else -> throw IllegalArgumentException("Cursor $this is not a resize cursor")
}

fun HorizontalDirection.invert() = when (this) {
    LEFT -> RIGHT
    RIGHT -> LEFT
}

fun VerticalDirection.invert() = when (this) {
    UP -> DOWN
    DOWN -> UP
}