package ponticello.ui.misc

import fxutils.actions.ContextualizedAction
import fxutils.actions.collectActions
import fxutils.runFXWithTimeout
import fxutils.styleClass
import javafx.application.Platform
import javafx.scene.control.TextArea
import org.kordamp.ikonli.Ikon
import org.kordamp.ikonli.materialdesign2.MaterialDesignA
import org.kordamp.ikonli.materialdesign2.MaterialDesignC
import ponticello.model.project.PonticelloProject
import ponticello.sc.client.ConsoleMonitor
import ponticello.sc.client.SuperColliderClient
import ponticello.ui.dock.Side
import ponticello.ui.dock.ToolPane
import ponticello.ui.dock.ToolPaneState

class ConsoleOutputPane(client: SuperColliderClient) : ToolPane(), ConsoleMonitor.Listener {
    override val type: Type
        get() = ConsoleOutputPane
    private val buffer = StringBuilder()

    override val content = TextArea() styleClass "console-output"

    override val headerActions: List<ContextualizedAction>
        get() = super.headerActions

    init {
        content.isEditable = false
        client.consoleMonitor.addListener(this)
    }

    override fun afterSetup() {
        scrollToEnd()
    }

    override fun defaultState(): ToolPaneState = ToolPaneState.docked

    override fun process(txt: String) {
        Platform.runLater {
            buffer.append(txt)
            if (buffer.length > 20000) buffer.delete(0, 10000)
            content.text = buffer.toString()
            runFXWithTimeout(20) {
                scrollToEnd()
            }
        }
    }

    private fun scrollToEnd() {
        content.scrollTop = Double.MAX_VALUE
    }

    companion object : Type(14, "Console Output") {
        override val icon: Ikon get() = MaterialDesignC.CONSOLE

        override val shortcuts get() = arrayOf("F11")

        override val defaultSide: Side
            get() = Side.BOTTOM

        override fun createToolPane(project: PonticelloProject): ToolPane = ConsoleOutputPane(project.client)

        private val actions = collectActions<ConsoleOutputPane> {
            addAction("Scroll Down") {
                icon(MaterialDesignA.ARROW_DOWN)
                executes { pane ->
                    pane.content.scrollTop = Double.MAX_VALUE
                }
            }
        }
    }
}