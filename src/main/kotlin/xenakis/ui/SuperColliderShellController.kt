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
import xenakis.impl.ConsoleMonitor
import xenakis.impl.SuperColliderClient
import kotlin.concurrent.thread

class SuperColliderShellController(private val client: SuperColliderClient) : ConsoleMonitor.Listener {
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

    @Suppress("unused") //called from xenakis/ui/shell.fxml
    fun submitCommand() {
        val command = commandField.text.ifEmpty { return }
        thread {
            client.eval(command).thenAccept { result ->
                Platform.runLater {
                    val l = makeHistoryLabel(command, result)
                    repl.children.add(0, l)
                    replScroll.vvalue = replScroll.vmin
                }
                history.add(command)
                historyPos = history.size
            }
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
        client.consoleMonitor.addListener(this)
    }

    override fun process(txt: String) {
        Platform.runLater {
            consoleOutput.text += txt
            consoleOutput.scrollTop = Double.MAX_VALUE
        }
    }

    companion object {
        fun createShellWindow(client: SuperColliderClient): Stage {
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