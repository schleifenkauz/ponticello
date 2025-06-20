package ponticello.ui.impl

import javafx.scene.Cursor
import javafx.scene.ImageCursor
import javafx.scene.image.Image
import ponticello.impl.Logger

object Cursors {
    val OPEN_HAND = createCursor("open-hand.png")
    val CLOSED_HAND = createCursor("closed-hand.png")
    val MOVE = createCursor("move.png")
    val CROSS_HAIR = createCursor("cross-hair.png")
    val RESIZE_HORIZONTAL = createCursor("resize-horizontal2.png")
    val RESIZE_VERTICAL = createCursor("resize-vertical2.png")

    private fun createCursor(imageName: String): Cursor {
        val stream = javaClass.getResourceAsStream("/ponticello/ui/cursors/$imageName")
        if (stream == null) {
            Logger.error("Could find cursor image: $imageName")
            return Cursor.DEFAULT
        }
        val image = Image(stream)
        return ImageCursor(image, image.width / 2.0, image.height / 2.0)
    }
}