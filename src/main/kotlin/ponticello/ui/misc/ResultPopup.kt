package ponticello.ui.misc

import fxutils.SubWindow
import fxutils.actions.action
import fxutils.actions.makeButton
import fxutils.actions.registerShortcuts
import fxutils.centerChildren
import fxutils.createBorder
import fxutils.infiniteSpace
import fxutils.show
import hextant.context.Context
import hextant.fx.initHextantScene
import javafx.geometry.Point2D
import javafx.scene.Node
import javafx.scene.input.Clipboard
import javafx.scene.input.DataFormat
import javafx.scene.layout.HBox
import javafx.scene.layout.Region
import javafx.scene.layout.VBox
import javafx.scene.paint.Color
import javafx.scene.text.Text
import org.kordamp.ikonli.materialdesign2.MaterialDesignC

class ResultPopup(
    context: Context,
    result: String, error: Boolean = false,
    layout: HBox = HBox()
) : SubWindow(layout, "Result", Type.Popup) {
    private val copyButton = copyResultAction.withContext(result).makeButton("medium-icon-button")
    private val resultText = Text(limitString(result, 100))

    init {
        resultText.fill = if (error) Color.RED else Color.WHITE
        layout.children.addAll(
            HBox(resultText).centerChildren(),
            VBox(infiniteSpace(), copyButton)
        )
        layout.border = createBorder(Color.GRAY, 2.0)
        layout.registerShortcuts(listOf(copyResultAction.withContext(result)))
        scene.fill = Color.BLACK
        scene.initHextantScene(context)
    }

    fun show(anchorNode: Region) {
        val popupHeight = resultText.prefHeight(-1.0) + 30.0
        val offset = Point2D(anchorNode.width + 10.0, anchorNode.height - popupHeight)
        show(anchorNode, offset)
    }

    companion object {
        private val copyResultAction = action<String>("Copy result") {
            shortcut("Ctrl+C")
            icon(MaterialDesignC.CONTENT_COPY)
            executes { result ->
                val content = mapOf(DataFormat.PLAIN_TEXT to result)
                Clipboard.getSystemClipboard().setContent(content)
            }
        }

        private fun limitString(input: String, maxLength: Int): String =
            if (input.length > maxLength) input.take(maxLength) + "..."
            else input
    }
}