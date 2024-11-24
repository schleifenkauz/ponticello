package xenakis.ui.misc

import hextant.context.Context
import javafx.application.Platform
import javafx.fxml.FXML
import javafx.fxml.FXMLLoader
import javafx.scene.Parent
import javafx.scene.control.Label
import javafx.scene.control.ScrollPane
import javafx.scene.control.TextArea
import javafx.scene.control.TextField
import javafx.scene.layout.VBox
import javafx.stage.Stage
import xenakis.sc.client.ConsoleMonitor
import xenakis.sc.client.SuperColliderClient
import xenakis.sc.client.SuperColliderException
import xenakis.ui.impl.SubWindow
import kotlin.concurrent.thread

class SuperColliderShellController(private val client: SuperColliderClient) : ConsoleMonitor.Listener {
    private val history = mutableListOf<String>()
    private var historyPos = 0

    @FXML
    private lateinit var commandField: TextField

    @FXML
    private lateinit var consoleOutput: TextArea

    @FXML
    private lateinit var historyPane: VBox

    @FXML
    private lateinit var historyScroll: ScrollPane

    @Suppress("unused") //called from xenakis/ui/shell.fxml
    fun submitCommand() {
        val command = commandField.text.ifEmpty { return }
        thread(isDaemon = true, name = "Shell evaluator") {
            val result = try {
                client.eval(command).get()
            } catch (e: SuperColliderException) {
                e.message ?: "Unknown error"
            }
            Platform.runLater {
                val l = makeHistoryLabel(command, result)
                historyPane.children.add(0, l)
                historyScroll.vvalue = historyScroll.vmin
            }
            history.add(command)
            historyPos = history.size
        }
        commandField.text = ""
    }

    private fun makeHistoryLabel(command: String?, result: String): Label {
        val l = Label("$command -> $result")
        l.isFocusTraversable = true
        l.styleClass.add("command-history-label")
        l.setOnMouseClicked { commandField.text = command }
        return l
    }

    @FXML
    fun initialize() {
        client.consoleMonitor.addListener(this)
        historyPane.maxHeight = Double.MAX_VALUE
        historyScroll.maxHeight = Double.MAX_VALUE
    }

    override fun process(txt: String) {
        Platform.runLater {
            consoleOutput.text += txt
            consoleOutput.scrollTop = Double.MAX_VALUE
        }
    }

    companion object {
        fun createShellWindow(context: Context): Stage {
            val client = context[SuperColliderClient]
            val controller = SuperColliderShellController(client)
            val loader = FXMLLoader()
            loader.location = controller.javaClass.getResource("shell.fxml")
            loader.setController(controller)
            val shell = loader.load<Parent>()
            val window = SubWindow(shell, "SuperCollider shell", context, SubWindow.Type.ToolWindow)
            window.setOnShown {
                controller.commandField.requestFocus()
            }
            window.width = 1000.0
            window.height = 1000.0
            return window
        }
    }
}