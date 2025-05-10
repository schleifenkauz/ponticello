package ponticello.ui.misc

import fxutils.SubWindow
import hextant.context.Context
import javafx.application.Platform
import javafx.geometry.Dimension2D
import javafx.scene.control.TextArea
import ponticello.sc.client.ConsoleMonitor
import ponticello.sc.client.SuperColliderClient
import ponticello.ui.impl.makeToolWindow

class SuperColliderOutputPane : TextArea(), ConsoleMonitor.Listener {
    private val buffer = StringBuilder()

    init {
        isEditable = false
    }

    override fun process(txt: String) {
        Platform.runLater {
            buffer.append(txt)
            if (buffer.length > 20000) buffer.delete(0, 10000)
            text = buffer.toString()
            scrollTop = Double.MAX_VALUE //TODO why doesn't it scroll to the end???
        }
    }

    companion object {
        fun createShellWindow(context: Context): SubWindow {
            val pane = SuperColliderOutputPane()
            val client = context[SuperColliderClient]
            client.consoleMonitor.addListener(pane)
            return context.makeToolWindow(
                pane, "SuperCollider output",
                defaultSize = Dimension2D(500.0, 1000.0)
            )
        }
    }
}