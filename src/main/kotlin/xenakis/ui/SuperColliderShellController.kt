package xenakis.ui

import javafx.application.Platform
import javafx.fxml.FXML
import javafx.fxml.FXMLLoader
import javafx.scene.Parent
import javafx.scene.Scene
import javafx.scene.control.Label
import javafx.scene.control.ScrollPane
import javafx.scene.control.TextArea
import javafx.scene.control.TextField
import javafx.scene.layout.VBox
import javafx.stage.Stage
import javafx.stage.Window
import xenakis.impl.UDPSuperColliderClient
import kotlin.concurrent.thread

class SuperColliderShellController(private val client: UDPSuperColliderClient) {
    private val history = mutableListOf<String>()
    private var historyPos = 0

    @FXML
    private lateinit var commandField: TextField

    @FXML
    private lateinit var consoleOutput: TextArea

    @FXML
    private lateinit var repl: VBox

    @FXML
    private lateinit var replScroll: ScrollPane

    fun submitCommand() {
        val command = commandField.text.ifEmpty { return }
        thread {
            val result = client.post(command)
            Platform.runLater {
                val l = makeHistoryLabel(command, result)
                repl.children.add(0, l)
                replScroll.vvalue = replScroll.vmin
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
        l.stylesheets.add(javaClass.getResource("style.css")!!.toExternalForm())
        l.setOnMouseClicked { commandField.text = command }
        return l
    }

    @FXML
    fun initialize() {
        client.addConsoleMonitor { output ->
            Platform.runLater {
                consoleOutput.text += output
                consoleOutput.scrollTop = Double.MAX_VALUE
            }
        }
    }

    companion object {
        fun createShellWindow(client: UDPSuperColliderClient): Stage {
            val controller = SuperColliderShellController(client)
            val loader = FXMLLoader()
            loader.location = controller.javaClass.getResource("shell.fxml")
            loader.setController(controller)
            val shell = loader.load<Parent>()
            val window = Stage()
            window.scene = Scene(shell)
            window.scene.stylesheets.add("/xenakis/ui/style.css")
            return window
        }
    }
}