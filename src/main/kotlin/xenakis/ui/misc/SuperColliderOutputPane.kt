package xenakis.ui.misc

import fxutils.SubWindow
import hextant.context.Context
import javafx.application.Platform
import javafx.geometry.Dimension2D
import javafx.scene.control.TextArea
import xenakis.sc.client.ConsoleMonitor
import xenakis.sc.client.SuperColliderClient
import xenakis.ui.impl.makeToolWindow

class SuperColliderOutputPane : TextArea(), ConsoleMonitor.Listener {
    private val buffer = StringBuilder()

    init {
        isEditable = false
    }

    override fun process(txt: String) {
        Platform.runLater {
            buffer.append(txt)
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